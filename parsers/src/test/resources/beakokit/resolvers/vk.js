// VK player resolver for Hibiki. Ported from the compiled-in VkExtractor (HTTP runtime - VK's
// video_ext.php embed is plain HTTP, no browser needed). Shipped as an external resolver, installed
// alongside any source that declares "vk" in resolverDependencies.
//
// Manifest hosts are limited to vk.com/vkvideo.ru: the compiled-in extractor also recognized
// ru.yummyani.me pages whose *path* is iframeVK.html, but the resolver manifest schema only
// matches by host - declaring yummyani.me here would make this resolver wrongly claim every
// non-VK embed YummyAnime serves from that same domain. The iframeVK.html branch is kept in
// resolveEmbedUrl below (faithful port, ready for a future path-aware manifest or a direct call),
// it's just not reachable via automatic host routing today - the compiled-in VkExtractor.kt stays
// as the fallback for that one case until the manifest schema grows path matching.

function S(value) { return value === null || value === undefined ? null : String(value); }

var DEFAULT_REFERER = "https://ru.yummyani.me/";
var DEFAULT_USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/137.0.0.0 Safari/537.36";
var KNOWN_QUALITIES = [144, 240, 360, 480, 720, 1080, 1440, 2160];
var QUALITY_KEYS = ["auto", "144p", "240p", "360p", "480p", "720p", "1080p", "1440p", "2160p"];

function mergeHeaders(base, extra) {
    var result = {};
    for (var key in base) result[key] = base[key];
    for (var key2 in extra) result[key2] = extra[key2];
    return result;
}

function normalizeUrl(url) {
    if (url.indexOf("//") === 0) return "https:" + url;
    if (url.indexOf("://") >= 0) return url;
    return "https://" + url;
}

function hostOf(url) {
    var match = /^https?:\/\/([^/?#]+)/.exec(url);
    return match !== null ? match[1] : null;
}

function pathOf(url) {
    var withoutScheme = url.replace(/^https?:\/\/[^/?#]+/, "");
    var path = withoutScheme.split("?")[0].split("#")[0];
    return path.length > 0 ? path : "/";
}

function decodeQueryValue(value) {
    try { return decodeURIComponent(value.replace(/\+/g, " ")); } catch (e) { return value; }
}

function queryParam(url, name) {
    var queryIndex = url.indexOf("?");
    if (queryIndex < 0) return null;
    var query = url.substring(queryIndex + 1).split("#")[0];
    var parts = query.split("&");
    for (var i = 0; i < parts.length; i++) {
        var kv = parts[i].split("=");
        if (decodeQueryValue(kv[0]) === name) {
            return kv.length > 1 ? decodeQueryValue(kv.slice(1).join("=")) : "";
        }
    }
    return null;
}

function endsWith(value, suffix) {
    return value.length >= suffix.length && value.substring(value.length - suffix.length) === suffix;
}

function buildVideoExtUrl(rawVideoId) {
    var match = /(-?\d+)_(\d+)/.exec(rawVideoId);
    if (match === null) throw new Error("Could not determine owner_id/video_id for VK");
    return "https://vk.com/video_ext.php?oid=" + encodeURIComponent(match[1]) +
        "&id=" + encodeURIComponent(match[2]) + "&js_api=1&partner_name=viqeo";
}

function resolveEmbedUrl(rawUrl) {
    var url = normalizeUrl(rawUrl);
    var host = (hostOf(url) || "").toLowerCase();
    var path = pathOf(url);

    if (endsWith(host, "yummyani.me") && path.toLowerCase().indexOf("iframevk.html") >= 0) {
        var idParam = (queryParam(url, "id") || "").trim().replace(/^video/, "");
        return buildVideoExtUrl(idParam);
    }
    if (endsWith(host, "vkvideo.ru")) {
        var pathMatch = /video(-?\d+)_(\d+)/.exec(path);
        return buildVideoExtUrl(pathMatch !== null ? pathMatch[1] + "_" + pathMatch[2] : "");
    }
    if (endsWith(host, "vk.com") && path.indexOf("video_ext.php") >= 0) {
        var ownerId = (queryParam(url, "oid") || "").trim();
        var videoId = (queryParam(url, "id") || "").trim();
        return buildVideoExtUrl(ownerId + "_" + videoId);
    }
    throw new Error("Unsupported VK link: " + rawUrl);
}

function decodeUnicodeEscapes(text) {
    if (text.indexOf("\\u") < 0) return text;
    var output = "";
    var index = 0;
    while (index < text.length) {
        var current = text.charAt(index);
        if (current === "\\" && index + 5 < text.length && text.charAt(index + 1) === "u") {
            var code = parseInt(text.substring(index + 2, index + 6), 16);
            if (!isNaN(code)) {
                output += String.fromCharCode(code);
                index += 6;
                continue;
            }
        }
        output += current;
        index += 1;
    }
    return output
        .replace(/\\n/g, "\n")
        .replace(/\\"/g, "\"")
        .replace(/\\'/g, "'")
        .replace(/\\\\/g, "\\");
}

function normalizeEscapedUrl(url) {
    return decodeUnicodeEscapes(url)
        .replace(/\\u0026/g, "&")
        .replace(/\\u002D/g, "-")
        .replace(/\\u002d/g, "-")
        .replace(/\\\//g, "/");
}

function normalizeCandidateUrl(rawUrl, baseUrl) {
    var trimmed = rawUrl.trim().replace(/^['"]+/, "").replace(/['"]+$/, "");
    var normalized = normalizeEscapedUrl(trimmed);
    if (normalized.length === 0) return "";
    if (normalized.indexOf("//") === 0) return "https:" + normalized;
    if (normalized.indexOf("http://") === 0) return normalized.replace("http://", "https://");
    if (normalized.indexOf("https://") === 0) return normalized;
    if (normalized.charAt(0) === "/") {
        var origin = (/^(https?:\/\/[^/]+)/.exec(baseUrl) || [])[1] || "https://ru.yummyani.me";
        return origin + normalized;
    }
    if (baseUrl) {
        var resolved = Jsoup.resolve(baseUrl, normalized);
        return resolved || normalized;
    }
    return normalized;
}

function isStreamLike(url, quality) {
    var lowered = url.toLowerCase();
    if (lowered.indexOf(".m3u8") >= 0 || lowered.indexOf(".mp4") >= 0 || lowered.indexOf(".mpd") >= 0) return true;
    var knownVkCdn = lowered.indexOf("okcdn.ru") >= 0 || lowered.indexOf("vkuser") >= 0 ||
        lowered.indexOf("userapi.com") >= 0 || lowered.indexOf("vkvd") >= 0;
    return knownVkCdn && quality !== "auto";
}

function addCandidate(candidates, order, quality, url) {
    if (!url) return;
    var cleaned = normalizeEscapedUrl(url);
    if (!isStreamLike(cleaned, quality)) return;
    for (var key in candidates) {
        if (key !== quality && candidates[key] === cleaned) return;
    }
    var current = candidates[quality];
    if (current === undefined) order.push(quality);
    if (current === undefined || current.length < cleaned.length) {
        candidates[quality] = cleaned;
    }
}

function orderQualityMap(candidates, insertionOrder) {
    var orderedKeys = [];
    var seen = {};
    for (var i = 0; i < QUALITY_KEYS.length; i++) {
        var qualityKey = QUALITY_KEYS[i];
        if (qualityKey in candidates && !seen[qualityKey]) { orderedKeys.push(qualityKey); seen[qualityKey] = true; }
        if (qualityKey === "auto") continue;
        var withoutSuffix = qualityKey.replace(/p$/, "");
        if (!seen[qualityKey] && withoutSuffix in candidates) {
            orderedKeys.push(qualityKey);
            seen[qualityKey] = true;
            candidates[qualityKey] = candidates[withoutSuffix];
        }
    }
    for (var j = 0; j < insertionOrder.length; j++) {
        var key2 = insertionOrder[j];
        if (!seen[key2]) { orderedKeys.push(key2); seen[key2] = true; }
    }
    return orderedKeys;
}

function qualityFromUrl(url) {
    var match = /(\d{3,4})(?=p?(?:\.mp4|\/|$))/i.exec(url);
    if (match === null) return "auto";
    var value = parseInt(match[1], 10);
    return KNOWN_QUALITIES.indexOf(value) >= 0 ? value + "p" : "auto";
}

function normalizeQualityLabel(label) {
    var normalized = label.trim().toLowerCase();
    if (normalized.length === 0 || normalized === "hls" || normalized === "hls_fmp4" || normalized === "url") return "auto";
    var match = /(\d{3,4})/.exec(normalized);
    var quality = match !== null ? parseInt(match[1], 10) : null;
    return (quality !== null && KNOWN_QUALITIES.indexOf(quality) >= 0) ? quality + "p" : label;
}

function qualityValue(label) {
    if (!label) return null;
    var match = /(\d{3,4})/.exec(label);
    return match !== null ? parseInt(match[1], 10) : null;
}

function buildPlaybackHeaders(requestHeaders, upstreamReferer) {
    var headers = {};
    for (var key in requestHeaders) {
        if (key && requestHeaders[key]) headers[key] = requestHeaders[key];
    }
    delete headers.Referer;
    delete headers.Referrer;
    delete headers["User-Agent"];
    delete headers.Accept;
    headers.Referer = upstreamReferer;
    headers["User-Agent"] = DEFAULT_USER_AGENT;
    headers.Accept = "*/*";
    return headers;
}

function extractFilesFromPrefetchCache(html) {
    var match = /(?:window\.)?cur\.apiPrefetchCache\s*=\s*(\[[\s\S]+?\]);/.exec(html);
    if (match === null) return null;
    var root;
    try { root = JSON.parse(match[1]); } catch (e) { return null; }
    for (var i = 0; i < root.length; i++) {
        var item = root[i];
        if (item && item.method === "video.getEmbed" && item.response && item.response.video && item.response.video.files) {
            return item.response.video.files;
        }
    }
    return null;
}

function addStreamsFromFiles(files, upstreamReferer, requestHeaders) {
    var headers = buildPlaybackHeaders(requestHeaders, upstreamReferer);
    var streams = [];

    if (files.hls_fmp4) streams.push({ url: files.hls_fmp4, type: "HLS", quality: null, headers: headers, segments: [] });
    if (files.hls) streams.push({ url: files.hls, type: "HLS", quality: null, headers: headers, segments: [] });
    if (files.dash_sep) streams.push({ url: files.dash_sep, type: "DASH", quality: null, headers: headers, segments: [] });

    var mp4Items = [];
    for (var key in files) {
        if (key.indexOf("mp4_") !== 0 || !files[key]) continue;
        var quality = parseInt(key.substring(4), 10);
        if (isNaN(quality)) continue;
        mp4Items.push({ quality: quality, url: files[key] });
    }
    mp4Items.sort(function (a, b) { return b.quality - a.quality; });
    for (var i = 0; i < mp4Items.length; i++) {
        streams.push({ url: mp4Items[i].url, type: "MP4", quality: mp4Items[i].quality + "p", headers: headers, segments: [] });
    }
    return streams;
}

function fallbackStreamsFromHtml(html, baseUrl, upstreamReferer, requestHeaders) {
    var candidates = {};
    var order = [];

    var namedPatterns = [
        ["1080p", /\b(?:url1080|mp4_1080)\b\s*[:=]\s*['"]([^'"]+)['"]/gi],
        ["720p", /\b(?:url720|mp4_720)\b\s*[:=]\s*['"]([^'"]+)['"]/gi],
        ["480p", /\b(?:url480|mp4_480)\b\s*[:=]\s*['"]([^'"]+)['"]/gi],
        ["360p", /\b(?:url360|mp4_360)\b\s*[:=]\s*['"]([^'"]+)['"]/gi],
        ["240p", /\b(?:url240|mp4_240)\b\s*[:=]\s*['"]([^'"]+)['"]/gi],
        ["auto", /\burl\b\s*[:=]\s*['"]([^'"]+)['"]/gi],
        ["auto", /"hls_fmp4"\s*:\s*['"]([^'"]+)['"]/gi],
        ["auto", /"hls"\s*:\s*['"]([^'"]+)['"]/gi],
    ];
    for (var i = 0; i < namedPatterns.length; i++) {
        var pattern = namedPatterns[i][1];
        var match;
        while ((match = pattern.exec(html)) !== null) {
            addCandidate(candidates, order, namedPatterns[i][0], normalizeCandidateUrl(match[1], baseUrl));
        }
    }

    var filesBlockMatch = /"files"\s*:\s*\{([\s\S]*?)\}\s*,\s*"trailer"/i.exec(html);
    if (filesBlockMatch !== null) {
        var block = filesBlockMatch[1].split("\"trailer\"").join("");
        var kvPattern = /"(mp4_\d{3,4}|hls_fmp4|hls|dash_sep|url\d{3,4}|url)"\s*:\s*['"]([^'"]+)['"]/gi;
        var kvMatch;
        while ((kvMatch = kvPattern.exec(block)) !== null) {
            addCandidate(candidates, order, normalizeQualityLabel(kvMatch[1]), normalizeCandidateUrl(kvMatch[2], baseUrl));
        }
    }

    var kvAllPattern = /"(mp4_\d{3,4}|hls_fmp4|hls|dash_sep|url\d{3,4}|url)"\s*:\s*['"]([^'"]+)['"]/gi;
    var kvAllMatch;
    while ((kvAllMatch = kvAllPattern.exec(html)) !== null) {
        addCandidate(candidates, order, normalizeQualityLabel(kvAllMatch[1]), normalizeCandidateUrl(kvAllMatch[2], baseUrl));
    }

    var streamUrlPatterns = [
        /https?:\\\/\\\/[^"'\s]+\.(?:m3u8|mp4|mpd)[^"'\s]*/gi,
        /\\\/\\\/[^"'\s]+\.(?:m3u8|mp4|mpd)[^"'\s]*/gi,
        /\b(?:videoUrl|fileList|file|src)\b[^=]*=\s*['"]([^'"]+\.(?:m3u8|mp4|mpd)[^'"]*)['"]/gi,
    ];
    for (var j = 0; j < streamUrlPatterns.length; j++) {
        var p2 = streamUrlPatterns[j];
        var m2;
        while ((m2 = p2.exec(html)) !== null) {
            var raw = (m2[1] !== undefined && m2[1] !== "") ? m2[1] : m2[0];
            addCandidate(candidates, order, qualityFromUrl(raw), normalizeCandidateUrl(raw, baseUrl));
        }
    }

    var dataSrcPatterns = [
        /data-video(?:-src|Src)\s*=\s*['"]([^'"]+)['"]/gi,
        /<source[^>]+src=['"]([^'"]+\.(?:m3u8|mp4|mpd)[^'"]*)['"]/gi,
    ];
    for (var k = 0; k < dataSrcPatterns.length; k++) {
        var p3 = dataSrcPatterns[k];
        var m3;
        while ((m3 = p3.exec(html)) !== null) {
            var raw2 = (m3[1] || "").trim();
            addCandidate(candidates, order, qualityFromUrl(raw2), normalizeCandidateUrl(raw2, baseUrl));
        }
    }

    var orderedKeys = orderQualityMap(candidates, order);
    var headers = buildPlaybackHeaders(requestHeaders, upstreamReferer);
    var streams = [];
    for (var oi = 0; oi < orderedKeys.length; oi++) {
        var quality2 = orderedKeys[oi];
        var url = candidates[quality2];
        var type = null;
        if (url.toLowerCase().indexOf(".m3u8") >= 0) type = "HLS";
        else if (url.toLowerCase().indexOf(".mpd") >= 0) type = "DASH";
        else if (quality2 !== "auto") type = "MP4";
        if (type === null) continue;
        streams.push({ url: url, type: type, quality: quality2 === "auto" ? null : quality2, headers: headers, segments: [] });
    }
    streams.sort(function (a, b) {
        var aHls = a.type === "HLS" ? 1 : 0;
        var bHls = b.type === "HLS" ? 1 : 0;
        if (aHls !== bHls) return bHls - aHls;
        return (qualityValue(b.quality) || 0) - (qualityValue(a.quality) || 0);
    });
    return streams;
}

var Provider = {
    resolve: function (linkJson) {
        var link = JSON.parse(linkJson);
        var embedUrl = resolveEmbedUrl(link.url);
        var linkHeaders = link.headers || {};
        var upstreamReferer = linkHeaders.Referer || linkHeaders.Referrer || DEFAULT_REFERER;

        var response = fetch(embedUrl, {
            headers: mergeHeaders(linkHeaders, {
                "Referer": upstreamReferer,
                "Accept": "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
                "User-Agent": DEFAULT_USER_AGENT,
            }),
        });
        if (!response.ok) throw new Error("VK embed returned HTTP " + response.status);

        var html = S(response.body);
        var files = extractFilesFromPrefetchCache(html);
        var streams = files !== null
            ? addStreamsFromFiles(files, upstreamReferer, linkHeaders)
            : fallbackStreamsFromHtml(html, embedUrl, upstreamReferer, linkHeaders);

        var seen = {};
        var distinctStreams = [];
        for (var i = 0; i < streams.length; i++) {
            var key = streams[i].url + "|" + streams[i].type;
            if (seen[key]) continue;
            seen[key] = true;
            distinctStreams.push(streams[i]);
        }

        if (distinctStreams.length === 0) throw new Error("VK embed returned no stream links");
        return distinctStreams;
    },
};
