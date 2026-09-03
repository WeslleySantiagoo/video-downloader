from pathlib import Path
from typing import Any

import boto3

from .config import get_settings


class ObjectStorage:
    def __init__(self, client: Any | None = None) -> None:
        self.settings = get_settings()
        self.client = client or boto3.client(
            "s3",
            endpoint_url=self.settings.s3_endpoint_url,
            region_name=self.settings.s3_region,
            aws_access_key_id=self.settings.s3_access_key_id,
            aws_secret_access_key=self.settings.s3_secret_access_key,
        )

    def check(self) -> None:
        self.client.head_bucket(Bucket=self.settings.s3_bucket)

    def upload(self, path: Path, object_key: str, content_type: str) -> None:
        self.client.upload_file(
            str(path),
            self.settings.s3_bucket,
            object_key,
            ExtraArgs={
                "ContentType": content_type,
                "ContentDisposition": f'attachment; filename="{path.name}"',
            },
        )

    def presigned_download(self, object_key: str) -> str:
        signing_client = self.client
        if self.settings.s3_public_endpoint_url:
            signing_client = boto3.client(
                "s3",
                endpoint_url=self.settings.s3_public_endpoint_url,
                region_name=self.settings.s3_region,
                aws_access_key_id=self.settings.s3_access_key_id,
                aws_secret_access_key=self.settings.s3_secret_access_key,
            )
        url = signing_client.generate_presigned_url(
            "get_object",
            Params={"Bucket": self.settings.s3_bucket, "Key": object_key},
            ExpiresIn=self.settings.media_retention_seconds,
        )
        return str(url)

    def delete(self, object_key: str) -> None:
        self.client.delete_object(Bucket=self.settings.s3_bucket, Key=object_key)
