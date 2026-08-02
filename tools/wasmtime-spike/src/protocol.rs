use serde::{Deserialize, Serialize};
use serde_json::Value;

pub const PROTOCOL_VERSION: u32 = 1;

#[derive(Debug, Deserialize, PartialEq, Serialize)]
pub enum Operation {
    #[serde(rename = "SEARCH")]
    Search,
    #[serde(rename = "DETAILS")]
    Details,
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
}

pub fn validate_search_payload(payload: &Value) -> Result<(), &'static str> {
    let object = payload.as_object().ok_or("payload must be a JSON object")?;
    required_string(object, "query")?;
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

fn required_string(
    object: &serde_json::Map<String, Value>,
    field: &'static str,
) -> Result<(), &'static str> {
    object
        .get(field)
        .and_then(Value::as_str)
        .filter(|value| !value.is_empty())
        .map(|_| ())
        .ok_or("required string field is missing or invalid")
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
        .ok_or("required non-negative integer field is missing or invalid")
}

fn optional_integer_or_null(
    object: &serde_json::Map<String, Value>,
    field: &'static str,
) -> Result<(), &'static str> {
    match object.get(field) {
        Some(Value::Null) | None => Ok(()),
        Some(value) if value.as_i64().is_some() => Ok(()),
        _ => Err("optional integer field is invalid"),
    }
}

fn optional_string_or_null(
    object: &serde_json::Map<String, Value>,
    field: &'static str,
) -> Result<(), &'static str> {
    match object.get(field) {
        Some(Value::Null) | None => Ok(()),
        Some(value) if value.as_str().is_some() => Ok(()),
        _ => Err("optional string field is invalid"),
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
        .ok_or("required string array field is missing or invalid")
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
        .ok_or("required object array field is missing or invalid")
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
    fn rejects_title_payload_without_required_collections() {
        assert_eq!(
            validate_title_payload(&serde_json::json!({
                "id": "title-1",
                "originalName": "Title"
            })),
            Err("required string array field is missing or invalid")
        );
    }
}
