// Kodik player resolver for Hibiki. Ported from the compiled-in KodikExtractor (HTTP runtime -
// Kodik's embed page and /ftor endpoint are plain HTTP, no browser needed). Shipped as an external
// resolver, installed alongside any source that declares "kodik" in resolverDependencies, so a
// protocol change (Kodik rotates its urlParams/endpoint scheme periodically) can ship without an
// APK release.

function S(value) { return value === null || value === undefined ? null : String(value); }

var BASE64_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";
var REQUIRED_URL_PARAM_KEYS = ["d", "d_sign", "pd", "pd_sign", "ref", "ref_sign"];

function base64Decode(input) {
    var cleaned = input.replace(/[^A-Za-z0-9+/]/g, "");
    var output = "";
    for (var i = 0; i < cleaned.length; i += 4) {
        var c0 = BASE64_ALPHABET.indexOf(cleaned.charAt(i));
        var c1 = BASE64_ALPHABET.indexOf(cleaned.charAt(i + 1));
        var c2 = i + 2 < cleaned.length ? BASE64_ALPHABET.indexOf(cleaned.charAt(i + 2)) : -1;
        var c3 = i + 3 < cleaned.length ? BASE64_ALPHABET.indexOf(cleaned.charAt(i + 3)) : -1;
        output += String.fromCharCode(((c0 << 2) | (c1 >> 4)) & 0xFF);
        if (c2 >= 0) output += String.fromCharCode((((c1 & 0xF) << 4) | (c2 >> 2)) & 0xFF);
        if (c3 >= 0) output += String.fromCharCode((((c2 & 0x3) << 6) | c3) & 0xFF);
    }
    return output;
}

function shiftLetter(ch) {
    var code = ch.charCodeAt(0);
    var isUpper = code >= 65 && code <= 90;
    var isLower = code >= 97 && code <= 122;
    if (!isUpper && !isLower) return ch;
    var base = isUpper ? 65 : 97;
    return String.fromCharCode(((code - base + 18) % 26) + base);
}

// Kodik obfuscates "src" fields by shifting every letter 18 places within its case before
// base64-encoding - this undoes that (shift back, then decode), same as the compiled-in extractor.
function decodeShiftedBase64(raw) {
    if (raw.indexOf("//") >= 0) return raw;
    var shifted = "";
    for (var i = 0; i < raw.length; i++) shifted += shiftLetter(raw.charAt(i));
    var padded = shifted;
    while (padded.length % 4 !== 0) padded += "=";
    return base64Decode(padded);
}

function decodeUrlParamIfNeeded(value) {
    if (value.indexOf("%") < 0 && value.indexOf("+") < 0) return value;
    try { return decodeURIComponent(value.replace(/\+/g, " ")); } catch (e) { return value; }
}

function originOf(url) {
    var match = /^(https?:\/\/[^/]+)/.exec(url);
    return match !== null ? match[1] : url;
}

function normalizeUrl(url) {
    if (url.indexOf("//") === 0) return "https:" + url;
    if (url.indexOf("://") >= 0) return url;
    return "https://" + url;
}

function mergeHeaders(base, extra) {
    var result = {};
    for (var key in base) result[key] = base[key];
    for (var key2 in extra) result[key2] = extra[key2];
    return result;
}

function parsePageInfo(html) {
    var urlParamsMatch = /\burlParams\s*=\s*'([^']+)'/.exec(html);
    var videoIdMatch = /\b(?:var\s+videoId|(?:videoInfo|vInfo)\.id)\s*=\s*["']([^"']+)["']/.exec(html);
    var typeMatch = /\b(?:var\s+type|(?:videoInfo|vInfo)\.type)\s*=\s*["']([^"']+)["']/.exec(html);
    var hashMatch = /\b(?:vInfo|videoInfo)\.hash\s*=\s*["']([^"']+)["']/.exec(html);
    if (urlParamsMatch === null) throw new Error("Kodik could not read urlParams from the embed page");
    if (videoIdMatch === null) throw new Error("Kodik could not read videoId from the embed page");
    if (typeMatch === null) throw new Error("Kodik could not read type from the embed page");
    if (hashMatch === null) throw new Error("Kodik could not read hash from the embed page");

    var rawParams = JSON.parse(urlParamsMatch[1]);
    var urlParams = {};
    for (var key in rawParams) {
        var value = S(rawParams[key]);
        urlParams[key] = key === "ref" ? decodeUrlParamIfNeeded(value) : value;
    }

    return { videoId: videoIdMatch[1], type: typeMatch[1], hash: hashMatch[1], urlParams: urlParams };
}

function normalizeScriptUrl(scriptUrl, pageOrigin) {
    if (scriptUrl.indexOf("//") === 0) return "https:" + scriptUrl;
    if (scriptUrl.charAt(0) === "/") return pageOrigin + scriptUrl;
    if (/^https?:/i.test(scriptUrl)) return scriptUrl;
    return pageOrigin + "/" + scriptUrl;
}

// Kodik moves its /ftor endpoint behind an obfuscated alias every so often; the alias is inlined
// as an atob(...) call inside the page's player script when present, falling back to /ftor when
// there's no such script (the common case).
function resolveEndpointUrl(html, pageUrl, pageOrigin, headers) {
    var scriptMatch = /src=["']((?:\/\/[^"']+)?\/assets\/js\/app\.player_single[^"']+)["']/i.exec(html);
    if (scriptMatch === null) return pageOrigin + "/ftor";

    var scriptUrl = normalizeScriptUrl(scriptMatch[1], pageOrigin);
    var scriptResponse;
    try {
        scriptResponse = fetch(scriptUrl, { headers: mergeHeaders(headers, { "Referer": pageUrl, "Accept": "*/*" }) });
    } catch (e) {
        return pageOrigin + "/ftor";
    }
    if (!scriptResponse.ok) return pageOrigin + "/ftor";

    var script = S(scriptResponse.body);
    var atobRegex = /atob\("([A-Za-z0-9+/=]+)"\)/g;
    var match;
    while ((match = atobRegex.exec(script)) !== null) {
        var decoded;
        try { decoded = base64Decode(match[1]); } catch (e2) { continue; }
        if (decoded.charAt(0) === "/" && decoded.charAt(1) !== "/" && decoded.length <= 12) {
            return pageOrigin + decoded;
        }
    }
    return pageOrigin + "/ftor";
}

function parseTimecodeMs(text) {
    var parts = text.trim().split(":");
    if (parts.length === 0 || parts.length > 3) return null;
    var multiplier = 1000, totalMs = 0;
    for (var i = parts.length - 1; i >= 0; i--) {
        var value = parseInt(parts[i], 10);
        if (isNaN(value)) return null;
        totalMs += value * multiplier;
        multiplier *= 60;
    }
    return totalMs;
}

function toVideoSegmentType(kind) {
    switch (String(kind || "").trim().toLowerCase()) {
        case "opening": case "op": case "intro": case "anime": return "OPENING";
        case "ending": case "ed": case "outro": return "ENDING";
        default: return "UNKNOWN";
    }
}

function parseSkipSegments(html) {
    var regex = /parseSkipButton\(\s*["']([^"']+)["']\s*,\s*["']([^"']*)["']\s*\)/g;
    var segments = [];
    var seen = {};
    var match;
    while ((match = regex.exec(html)) !== null) {
        var parts = match[1].split("-");
        if (parts.length !== 2) continue;
        var startMs = parseTimecodeMs(parts[0]);
        var endMs = parseTimecodeMs(parts[1]);
        if (startMs === null || endMs === null || endMs <= startMs) continue;
        var type = toVideoSegmentType(match[2]);
        var key = type + ":" + startMs + ":" + endMs;
        if (seen[key]) continue;
        seen[key] = true;
        segments.push({ type: type, startMs: startMs, endMs: endMs });
    }
    return segments;
}

function qualityValue(quality) {
    var digits = String(quality).replace(/[^0-9]/g, "");
    return digits.length > 0 ? parseInt(digits, 10) : null;
}

function repairManifestQuality(url, expectedQuality) {
    var match = /\/(\d+)\.mp4:hls:manifest\.m3u8(?=$|[?#])/.exec(url);
    if (match === null) return url;
    var actualQuality = parseInt(match[1], 10);
    if (isNaN(actualQuality) || actualQuality >= expectedQuality) return url;
    return url.substring(0, match.index) + "/" + expectedQuality + ".mp4:hls:manifest.m3u8" +
        url.substring(match.index + match[0].length);
}

function streamTypeFor(itemType, url) {
    var lowerType = String(itemType || "").toLowerCase();
    var cleanUrl = url.split("?")[0].split("#")[0].toLowerCase();
    if (lowerType.indexOf("mpegurl") >= 0 || lowerType.indexOf("m3u8") >= 0 || cleanUrl.lastIndexOf(".m3u8") === cleanUrl.length - 5) return "HLS";
    if (lowerType.indexOf("mpd") >= 0 || cleanUrl.lastIndexOf(".mpd") === cleanUrl.length - 4) return "DASH";
    return "MP4";
}

function buildPlaybackHeaders(inputHeaders, pageUrl) {
    var headers = {};
    for (var key in inputHeaders) {
        if (key && inputHeaders[key]) headers[key] = inputHeaders[key];
    }
    delete headers["Referer"];
    delete headers["Referrer"];
    headers["Referer"] = pageUrl;
    return headers;
}

function formEncode(params) {
    var parts = [];
    for (var key in params) {
        parts.push(
            encodeURIComponent(key).replace(/%20/g, "+") + "=" +
            encodeURIComponent(String(params[key])).replace(/%20/g, "+"),
        );
    }
    return parts.join("&");
}

var Provider = {
    resolve: function (linkJson) {
        var link = JSON.parse(linkJson);
        var pageUrl = normalizeUrl(link.url);
        var pageOrigin = originOf(pageUrl);
        var pageHeaders = link.headers || {};

        var pageResponse = fetch(pageUrl, {
            headers: mergeHeaders(pageHeaders, { "Accept": "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8" }),
        });
        if (!pageResponse.ok) throw new Error("Kodik returned HTTP " + pageResponse.status);
        var html = S(pageResponse.body);
        var setCookie = pageResponse.headers ? pageResponse.headers["set-cookie"] : null;
        var cookieHeader = setCookie ? S(setCookie).split(";")[0] : null;

        var pageInfo = parsePageInfo(html);
        var endpointUrl = resolveEndpointUrl(html, pageUrl, pageOrigin, pageHeaders);
        var segments = parseSkipSegments(html);

        var missing = [];
        for (var i = 0; i < REQUIRED_URL_PARAM_KEYS.length; i++) {
            if (!(REQUIRED_URL_PARAM_KEYS[i] in pageInfo.urlParams)) missing.push(REQUIRED_URL_PARAM_KEYS[i]);
        }
        if (missing.length > 0) {
            throw new Error("Kodik did not expose required urlParams: " + missing.join(", "));
        }

        var formParams = {};
        for (var j = 0; j < REQUIRED_URL_PARAM_KEYS.length; j++) {
            formParams[REQUIRED_URL_PARAM_KEYS[j]] = pageInfo.urlParams[REQUIRED_URL_PARAM_KEYS[j]];
        }
        formParams.bad_user = "false";
        formParams.cdn_is_working = "false";
        formParams.type = pageInfo.type;
        formParams.hash = pageInfo.hash;
        formParams.id = pageInfo.videoId;
        formParams.info = "{}";

        var postHeaders = mergeHeaders(pageHeaders, {
            "Accept": "application/json, text/javascript, */*; q=0.01",
            "Origin": pageOrigin,
            "Referer": pageUrl,
            "X-Requested-With": "XMLHttpRequest",
            "Content-Type": "application/x-www-form-urlencoded",
        });
        if (cookieHeader) postHeaders.Cookie = cookieHeader;

        var ftorResponse = fetch(endpointUrl, {
            method: "POST",
            headers: postHeaders,
            body: formEncode(formParams),
        });
        if (!ftorResponse.ok) throw new Error("Kodik returned HTTP " + ftorResponse.status + " from " + endpointUrl);

        var ftor = JSON.parse(S(ftorResponse.body));
        var links = ftor.links || {};
        var candidates = [];
        var seen = {};
        for (var quality in links) {
            var numericQuality = qualityValue(quality);
            if (numericQuality === null) continue;
            var items = links[quality] || [];
            for (var idx = 0; idx < items.length; idx++) {
                var item = items[idx];
                if (!item.src) continue;
                var source = decodeShiftedBase64(item.src);
                var url = repairManifestQuality(source, numericQuality);
                var qualityLabel = numericQuality + "p";
                var key = qualityLabel + "|" + url;
                if (seen[key]) continue;
                seen[key] = true;
                candidates.push({
                    url: url, type: streamTypeFor(item.type, source), quality: qualityLabel,
                    headers: buildPlaybackHeaders(pageHeaders, pageUrl), segments: segments,
                });
            }
        }
        candidates.sort(function (a, b) { return (qualityValue(b.quality) || 0) - (qualityValue(a.quality) || 0); });
        if (candidates.length === 0) throw new Error("Kodik returned no playable qualities");
        return candidates;
    },
};
