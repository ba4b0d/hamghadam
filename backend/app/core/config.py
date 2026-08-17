"""Application settings loaded from environment / .env file."""

from functools import lru_cache

from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_file=".env", env_file_encoding="utf-8", extra="ignore")

    app_env: str = "dev"
    database_url: str = "sqlite:///./fitness.db"
    secret_key: str = "dev-secret-change-me-0123456789abcdef0123456789"
    access_token_expire_minutes: int = 60 * 24  # 24h
    cors_origins: list[str] = ["*"]
    log_level: str = "INFO"

    # FCM push (BE-C3). Dry-run is the default so tests/CI never touch the
    # network: notifications are logged and no delivery rows are written.
    # Production: set fcm_credentials_path to the Firebase service-account
    # JSON and fcm_dry_run=false.
    fcm_credentials_path: str = ""
    fcm_dry_run: bool = True

    # Google Auth & User Profiles (V1.2 Social)
    google_web_client_id: str = ""
    avatar_dir: str = "static/avatars"
    avatar_base_url: str = "https://api.hamghadam.ba4b0d.ir/static/avatars"
    max_avatar_size_bytes: int = 5 * 1024 * 1024  # 5MB

    @property
    def is_prod(self) -> bool:
        return self.app_env.lower() in {"prod", "production"}


@lru_cache
def get_settings() -> Settings:
    return Settings()


settings = get_settings()
