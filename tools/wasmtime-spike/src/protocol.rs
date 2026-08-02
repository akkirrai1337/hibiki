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

pub fn run_roundtrip_probe() -> Result<(), Box<dyn std::error::Error>> {
    let request = Request {
        request_id: "rust-probe-1".to_owned(),
        operation: Operation::Search,
        payload: serde_json::json!({ "query": "frieren" }),
        protocol_version: PROTOCOL_VERSION,
    };
    request.validate()?;

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
}
