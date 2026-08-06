use serde::{Deserialize, Serialize};
use serde_json::Value;

pub const PROTOCOL_VERSION: u32 = 1;

#[derive(Debug, Deserialize, PartialEq, Serialize)]
pub enum Operation {
    #[serde(rename = "SEARCH")]
    Search,
    #[serde(rename = "FILTER_CATALOG")]
    FilterCatalog,
    #[serde(rename = "DETAILS")]
    Details,
    #[serde(rename = "PLAYBACK_GROUPS")]
    PlaybackGroups,
    #[serde(rename = "PLAYER_LINKS")]
    PlayerLinks,
}

#[derive(Debug, Deserialize, PartialEq, Serialize)]
pub enum ErrorCode {
    #[serde(rename = "INVALID_REQUEST")]
    InvalidRequest,
    #[serde(rename = "HOST_ACCESS_DENIED")]
    HostAccessDenied,
    #[serde(rename = "SOURCE_FAILURE")]
    SourceFailure,
    #[serde(rename = "RUNTIME_FAILURE")]
    RuntimeFailure,
    #[serde(rename = "CANCELLED")]
    Cancelled,
}

#[derive(Debug, Deserialize, PartialEq, Serialize)]
pub struct Request {
    #[serde(rename = "requestId")]
    pub request_id: String,
    pub operation: Operation,
    pub payload: Value,
    #[serde(rename = "protocolVersion")]
    pub protocol_version: u32,
}

impl Request {
    pub fn from_value(value: &Value) -> Result<Self, Box<dyn std::error::Error>> {
        let request: Self = serde_json::from_value(value.clone())?;
        request.validate()?;
        match request.operation {
            Operation::Search => validate_search_payload(&request.payload)?,
            Operation::FilterCatalog => validate_filter_catalog_request(&request.payload)?,
            Operation::Details => validate_details_payload(&request.payload)?,
            Operation::PlaybackGroups => validate_playback_groups_payload(&request.payload)?,
            Operation::PlayerLinks => validate_player_links_payload(&request.payload)?,
        }
        Ok(request)
    }

    pub fn validate(&self) -> Result<(), &'static str> {
        if self.request_id.trim().is_empty() {
            return Err("request ID must not be blank");
        }
        if self.protocol_version != PROTOCOL_VERSION {
            return Err("unsupported protocol version");
        }
        if !self.payload.is_object() {
            return Err("payload must be a JSON object");
        }
        Ok(())
    }
}

#[derive(Debug, Deserialize, PartialEq, Serialize)]
pub struct Response {
    #[serde(rename = "requestId")]
    pub request_id: String,
    pub payload: Option<Value>,
    #[serde(rename = "errorCode")]
    pub error_code: Option<ErrorCode>,
    #[serde(rename = "errorMessage")]
    pub error_message: Option<String>,
    #[serde(rename = "protocolVersion")]
    pub protocol_version: u32,
}

impl Response {
    pub fn validate(&self) -> Result<(), &'static str> {
        if self.request_id.trim().is_empty() {
            return Err("request ID must not be blank");
        }
        if self.protocol_version != PROTOCOL_VERSION {
            return Err("unsupported protocol version");
        }
        if self.payload.is_some() == self.error_code.is_some() {
            return Err("response must contain either payload or error");
        }
        if self.error_code.is_none() && self.error_message.is_some() {
            return Err("successful response must not contain an error message");
        }
        if let Some(payload) = &self.payload {
            if !payload.is_object() {
                return Err("payload must be a JSON object");
            }
        }
        Ok(())
    }

    pub fn validate_for_request(&self, request: &Request) -> Result<(), &'static str> {
        self.validate()?;
        if self.request_id != request.request_id {
            return Err("response request ID does not match request");
        }
        if let Some(payload) = &self.payload {
            match &request.operation {
                Operation::Search => validate_search_response_payload(payload)?,
                Operation::FilterCatalog => validate_filter_catalog_response_payload(payload)?,
                Operation::Details => validate_title_payload(payload)?,
                Operation::PlaybackGroups => validate_playback_groups_response_payload(payload)?,
                Operation::PlayerLinks => validate_player_links_response_payload(payload)?,
            }
        }
        Ok(())
    }
}

pub fn validate_search_payload(payload: &Value) -> Result<(), &'static str> {
    let object = payload.as_object().ok_or("payload must be a JSON object")?;
    required_string_value(object, "query")?;
    required_non_negative_integer(object, "limit")?;
    required_non_negative_integer(object, "offset")?;
    required_string(object, "sort")?;
    for field in [
        "typeAliases",
        "statusAliases",
        "includedGenreAliases",
        "excludedGenreAliases",
    ] {
        required_string_array(object, field)?;
    }
    optional_integer_or_null(object, "yearFrom")?;
    optional_integer_or_null(object, "yearTo")?;
    Ok(())
}

fn validate_filter_catalog_request(payload: &Value) -> Result<(), &'static str> {
    payload
        .as_object()
        .map(|_| ())
        .ok_or("payload must be a JSON object")
}

fn validate_filter_catalog_response_payload(payload: &Value) -> Result<(), &'static str> {
    let object = payload
        .as_object()
        .ok_or("filter catalog payload must be a JSON object")?;
    for field in ["sortOptions", "typeOptions", "statusOptions", "genreOptions"] {
        let options = object
            .get(field)
            .and_then(Value::as_array)
            .ok_or(match field {
                "sortOptions" => "filter catalog array is missing or invalid: sortOptions",
                "typeOptions" => "filter catalog array is missing or invalid: typeOptions",
                "statusOptions" => "filter catalog array is missing or invalid: statusOptions",
                "genreOptions" => "filter catalog array is missing or invalid: genreOptions",
                _ => "filter catalog array is missing or invalid",
            })?;
        for option in options {
            let option = option
                .as_object()
                .ok_or("filter catalog option must be an object")?;
            required_string(option, "id")?;
            required_string(option, "title")?;
        }
    }
    Ok(())
}

pub fn validate_details_payload(payload: &Value) -> Result<(), &'static str> {
    let object = payload.as_object().ok_or("payload must be a JSON object")?;
    required_string(object, "id")
}

pub fn validate_playback_groups_payload(payload: &Value) -> Result<(), &'static str> {
    let object = payload.as_object().ok_or("payload must be a JSON object")?;
    required_string(object, "titleId")
}

pub fn validate_player_links_payload(payload: &Value) -> Result<(), &'static str> {
    let object = payload.as_object().ok_or("payload must be a JSON object")?;
    for field in ["titleId", "groupId", "episodeId"] {
        required_string(object, field)?;
    }
    required_number(object, "episodeNumber")
}

pub fn validate_search_response_payload(payload: &Value) -> Result<(), &'static str> {
    let object = payload
        .as_object()
        .ok_or("search response payload must be a JSON object")?;
    let items = object
        .get("items")
        .and_then(Value::as_array)
        .ok_or("search response items must be an array")?;
    for item in items {
        validate_title_payload(item)?;
    }
    Ok(())
}

pub fn validate_title_payload(payload: &Value) -> Result<(), &'static str> {
    let object = payload
        .as_object()
        .ok_or("title payload must be a JSON object")?;
    for field in ["id", "originalName"] {
        required_string(object, field)?;
    }
    for field in ["synonyms", "genres", "screenshots", "studios"] {
        required_string_array(object, field)?;
    }
    for field in [
        "ratings",
        "mainCharacters",
        "similarAnime",
        "franchiseAnime",
        "relatedAnime",
    ] {
        required_object_array(object, field)?;
    }
    for field in [
        "russianName",
        "englishName",
        "japaneseName",
        "type",
        "posterUrl",
        "status",
        "description",
        "ageRating",
        "sourceMaterial",
        "posterFallbackUrl",
    ] {
        optional_string_or_null(object, field)?;
    }
    for field in [
        "year",
        "episodeCount",
        "nextEpisodeAt",
        "viewCount",
        "season",
        "availableEpisodeCount",
    ] {
        optional_integer_or_null(object, field)?;
    }
    if let Some(trailer) = object.get("trailer") {
        if !trailer.is_null() && !trailer.is_object() {
            return Err("trailer must be an object or null");
        }
    }
    Ok(())
}

pub fn validate_playback_groups_response_payload(payload: &Value) -> Result<(), &'static str> {
    let object = payload
        .as_object()
        .ok_or("playback groups response payload must be a JSON object")?;
    let groups = object
        .get("groups")
        .and_then(Value::as_array)
        .ok_or("playback groups must be an array")?;
    for group in groups {
        let group = group
            .as_object()
            .ok_or("playback group must be an object")?;
        required_string(group, "id")?;
        required_string(group, "title")?;
        optional_string_or_null(group, "qualityLabel")?;
        let episodes = group
            .get("episodes")
            .and_then(Value::as_array)
            .ok_or("playback group episodes must be an array")?;
        for episode in episodes {
            let episode = episode.as_object().ok_or("episode must be an object")?;
            required_string(episode, "id")?;
            required_number(episode, "number")?;
            optional_string_or_null(episode, "title")?;
        }
    }
    Ok(())
}

pub fn validate_player_links_response_payload(payload: &Value) -> Result<(), &'static str> {
    let object = payload
        .as_object()
        .ok_or("player links response payload must be a JSON object")?;
    let links = object
        .get("links")
        .and_then(Value::as_array)
        .ok_or("player links must be an array")?;
    for link in links {
        let link = link.as_object().ok_or("player link must be an object")?;
        required_string(link, "url")?;
        required_string(link, "type")?;
        optional_string_or_null(link, "quality")?;
        optional_string_or_null(link, "playerName")?;
        optional_string_or_null(link, "translation")?;
        if let Some(headers) = link.get("headers") {
            if !headers.is_object() {
                return Err("player link headers must be an object");
            }
        }
        if let Some(segments) = link.get("segments") {
            if !segments.is_array() || !segments.as_array().unwrap().iter().all(Value::is_object) {
                return Err("player link segments must be an object array");
            }
        }
        optional_integer_or_null(link, "videoId")?;
    }
    Ok(())
}

fn required_string(
    object: &serde_json::Map<String, Value>,
    field: &'static str,
) -> Result<(), &'static str> {
    object
        .get(field)
        .and_then(Value::as_str)
        .filter(|value| !value.is_empty())
        .map(|_| ())
        .ok_or(match field {
            "query" => "required string field is missing or invalid: query",
            "sort" => "required string field is missing or invalid: sort",
            "id" => "required string field is missing or invalid: id",
            "originalName" => "required string field is missing or invalid: originalName",
            "titleId" => "required string field is missing or invalid: titleId",
            "groupId" => "required string field is missing or invalid: groupId",
            "episodeId" => "required string field is missing or invalid: episodeId",
            "url" => "required string field is missing or invalid: url",
            "type" => "required string field is missing or invalid: type",
            "title" => "required string field is missing or invalid: title",
            _ => "required string field is missing or invalid",
        })
}

fn required_string_value(
    object: &serde_json::Map<String, Value>,
    field: &'static str,
) -> Result<(), &'static str> {
    object
        .get(field)
        .and_then(Value::as_str)
        .map(|_| ())
        .ok_or(match field {
            "query" => "required string field is missing or invalid: query",
            _ => "required string field is missing or invalid",
        })
}

fn required_non_negative_integer(
    object: &serde_json::Map<String, Value>,
    field: &'static str,
) -> Result<(), &'static str> {
    object
        .get(field)
        .and_then(Value::as_i64)
        .filter(|value| *value >= 0)
        .map(|_| ())
        .ok_or(match field {
            "limit" => "required non-negative integer field is missing or invalid: limit",
            "offset" => "required non-negative integer field is missing or invalid: offset",
            _ => "required non-negative integer field is missing or invalid",
        })
}

fn required_number(
    object: &serde_json::Map<String, Value>,
    field: &'static str,
) -> Result<(), &'static str> {
    object
        .get(field)
        .filter(|value| value.as_f64().is_some())
        .map(|_| ())
        .ok_or(match field {
            "number" => "required number field is missing or invalid: number",
            "episodeNumber" => "required number field is missing or invalid: episodeNumber",
            _ => "required number field is missing or invalid",
        })
}

fn optional_integer_or_null(
    object: &serde_json::Map<String, Value>,
    field: &'static str,
) -> Result<(), &'static str> {
    match object.get(field) {
        Some(Value::Null) | None => Ok(()),
        Some(value) if value.as_i64().is_some() => Ok(()),
        _ => Err(match field {
            "yearFrom" => "optional integer field is invalid: yearFrom",
            "yearTo" => "optional integer field is invalid: yearTo",
            _ => "optional integer field is invalid",
        }),
    }
}

fn optional_string_or_null(
    object: &serde_json::Map<String, Value>,
    field: &'static str,
) -> Result<(), &'static str> {
    match object.get(field) {
        Some(Value::Null) | None => Ok(()),
        Some(value) if value.as_str().is_some() => Ok(()),
        _ => Err(match field {
            "russianName" => "optional string field is invalid: russianName",
            "englishName" => "optional string field is invalid: englishName",
            "originalName" => "optional string field is invalid: originalName",
            _ => "optional string field is invalid",
        }),
    }
}

fn required_string_array(
    object: &serde_json::Map<String, Value>,
    field: &'static str,
) -> Result<(), &'static str> {
    object
        .get(field)
        .and_then(Value::as_array)
        .filter(|values| values.iter().all(Value::is_string))
        .map(|_| ())
        .ok_or(match field {
            "typeAliases" => "required string array field is missing or invalid: typeAliases",
            "statusAliases" => "required string array field is missing or invalid: statusAliases",
            "includedGenreAliases" => "required string array field is missing or invalid: includedGenreAliases",
            "excludedGenreAliases" => "required string array field is missing or invalid: excludedGenreAliases",
            "synonyms" => "required string array field is missing or invalid: synonyms",
            "genres" => "required string array field is missing or invalid: genres",
            "screenshots" => "required string array field is missing or invalid: screenshots",
            "studios" => "required string array field is missing or invalid: studios",
            _ => "required string array field is missing or invalid",
        })
}

fn required_object_array(
    object: &serde_json::Map<String, Value>,
    field: &'static str,
) -> Result<(), &'static str> {
    object
        .get(field)
        .and_then(Value::as_array)
        .filter(|values| values.iter().all(Value::is_object))
        .map(|_| ())
        .ok_or(match field {
            "ratings" => "required object array field is missing or invalid: ratings",
            "mainCharacters" => "required object array field is missing or invalid: mainCharacters",
            "similarAnime" => "required object array field is missing or invalid: similarAnime",
            "franchiseAnime" => "required object array field is missing or invalid: franchiseAnime",
            "relatedAnime" => "required object array field is missing or invalid: relatedAnime",
            _ => "required object array field is missing or invalid",
        })
}

pub fn run_roundtrip_probe() -> Result<(), Box<dyn std::error::Error>> {
    let request = Request {
        request_id: "rust-probe-1".to_owned(),
        operation: Operation::Search,
        payload: serde_json::json!({
            "query": "frieren",
            "limit": 20,
            "offset": 0,
            "sort": "RELEVANCE",
            "typeAliases": [],
            "statusAliases": [],
            "includedGenreAliases": [],
            "excludedGenreAliases": [],
            "yearFrom": null,
            "yearTo": null
        }),
        protocol_version: PROTOCOL_VERSION,
    };
    request.validate()?;
    validate_search_payload(&request.payload)?;

    let encoded = serde_json::to_string(&request)?;
    let decoded: Request = serde_json::from_str(&encoded)?;
    decoded.validate()?;
    assert_eq!(decoded, request);

    let response = Response {
        request_id: request.request_id,
        payload: Some(serde_json::json!({ "items": [] })),
        error_code: None,
        error_message: None,
        protocol_version: PROTOCOL_VERSION,
    };
    response.validate()?;
    validate_title_payload(&serde_json::json!({
        "id": "title-1",
        "russianName": null,
        "englishName": "Title",
        "originalName": "Title",
        "japaneseName": null,
        "synonyms": [],
        "year": null,
        "type": null,
        "episodeCount": null,
        "posterUrl": null,
        "status": null,
        "description": null,
        "nextEpisodeAt": null,
        "genres": [],
        "ratings": [],
        "ageRating": null,
        "viewCount": null,
        "screenshots": [],
        "trailer": null,
        "sourceMaterial": null,
        "studios": [],
        "mainCharacters": [],
        "similarAnime": [],
        "franchiseAnime": [],
        "relatedAnime": [],
        "season": null,
        "availableEpisodeCount": null,
        "posterFallbackUrl": null
    }))?;
    println!("runtime protocol: Rust JSON round-trip OK");
    Ok(())
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn request_uses_kotlin_compatible_field_names() {
        let request = Request {
            request_id: "request-1".to_owned(),
            operation: Operation::Details,
            payload: serde_json::json!({ "id": "title-1" }),
            protocol_version: PROTOCOL_VERSION,
        };

        let json = serde_json::to_value(&request).unwrap();

        assert_eq!(json["requestId"], "request-1");
        assert_eq!(json["operation"], "DETAILS");
        assert_eq!(json["protocolVersion"], 1);
    }

    #[test]
    fn response_requires_payload_or_error() {
        let response = Response {
            request_id: "request-1".to_owned(),
            payload: None,
            error_code: None,
            error_message: None,
            protocol_version: PROTOCOL_VERSION,
        };

        assert_eq!(
            response.validate(),
            Err("response must contain either payload or error")
        );
    }

    #[test]
    fn validates_the_complete_kotlin_search_payload_shape() {
        let payload = serde_json::json!({
            "query": "frieren",
            "limit": 20,
            "offset": 0,
            "sort": "RELEVANCE",
            "typeAliases": [],
            "statusAliases": [],
            "includedGenreAliases": [],
            "excludedGenreAliases": [],
            "yearFrom": null,
            "yearTo": null
        });

        assert_eq!(validate_search_payload(&payload), Ok(()));
    }

    #[test]
    fn accepts_empty_search_query_for_catalog_requests() {
        let payload = serde_json::json!({
            "query": "",
            "limit": 20,
            "offset": 0,
            "sort": "RELEVANCE",
            "typeAliases": [],
            "statusAliases": [],
            "includedGenreAliases": [],
            "excludedGenreAliases": [],
            "yearFrom": null,
            "yearTo": null
        });

        assert_eq!(validate_search_payload(&payload), Ok(()));
    }

    #[test]
    fn validates_the_details_payload_shape() {
        assert_eq!(
            validate_details_payload(&serde_json::json!({ "id": "title-1" })),
            Ok(())
        );
        assert!(validate_details_payload(&serde_json::json!({ "query": "title-1" })).is_err());
    }

    #[test]
    fn validates_playback_request_and_response_shapes() {
        let groups_request = Request {
            request_id: "request-1".to_owned(),
            operation: Operation::PlaybackGroups,
            payload: serde_json::json!({ "titleId": "title-1" }),
            protocol_version: PROTOCOL_VERSION,
        };
        assert!(Request::from_value(&serde_json::to_value(groups_request).unwrap()).is_ok());
        assert!(
            validate_playback_groups_response_payload(&serde_json::json!({ "groups": [{
                "id": "group-1",
                "title": "Dub",
                "qualityLabel": null,
                "episodes": [{ "id": "episode-1", "number": 1.0, "title": "Episode 1" }]
            }] }))
            .is_ok()
        );

        let links_request = Request {
            request_id: "request-2".to_owned(),
            operation: Operation::PlayerLinks,
            payload: serde_json::json!({
                "titleId": "title-1",
                "groupId": "group-1",
                "episodeId": "episode-1",
                "episodeNumber": 1.0
            }),
            protocol_version: PROTOCOL_VERSION,
        };
        assert!(Request::from_value(&serde_json::to_value(links_request).unwrap()).is_ok());
        assert!(
            validate_player_links_response_payload(&serde_json::json!({ "links": [{
                "url": "https://video.example/episode-1.m3u8",
                "type": "DIRECT_HLS",
                "quality": "1080p",
                "headers": {},
                "segments": [],
                "videoId": null
            }] }))
            .is_ok()
        );
    }

    #[test]
    fn validates_response_against_request_operation_and_id() {
        let request = Request {
            request_id: "request-1".to_owned(),
            operation: Operation::Details,
            payload: serde_json::json!({ "id": "title-1" }),
            protocol_version: PROTOCOL_VERSION,
        };
        let response = Response {
            request_id: "request-1".to_owned(),
            payload: Some(serde_json::json!({
                "id": "title-1",
                "originalName": "Title",
                "synonyms": [],
                "genres": [],
                "ratings": [],
                "screenshots": [],
                "studios": [],
                "mainCharacters": [],
                "similarAnime": [],
                "franchiseAnime": [],
                "relatedAnime": []
            })),
            error_code: None,
            error_message: None,
            protocol_version: PROTOCOL_VERSION,
        };

        assert_eq!(response.validate_for_request(&request), Ok(()));
        let mismatched_request = Request {
            request_id: "other-request".to_owned(),
            operation: Operation::Details,
            payload: serde_json::json!({ "id": "title-1" }),
            protocol_version: PROTOCOL_VERSION,
        };
        assert_eq!(
            response.validate_for_request(&mismatched_request),
            Err("response request ID does not match request")
        );
    }

    #[test]
    fn rejects_search_response_with_incomplete_title() {
        assert_eq!(
            validate_search_response_payload(&serde_json::json!({
                "items": [{ "id": "title-1" }]
            })),
            Err("required string field is missing or invalid: originalName")
        );
    }

    #[test]
    fn rejects_title_payload_without_required_collections() {
        assert_eq!(
            validate_title_payload(&serde_json::json!({
                "id": "title-1",
                "originalName": "Title"
            })),
            Err("required string array field is missing or invalid: synonyms")
        );
    }
}
