//! The server's `config.toml`. Shared with the desktop app, which edits it,
//! so the type lives in `core::local::config`.

pub use friday_mobile_core::local::config::{Config, ConfigError};
