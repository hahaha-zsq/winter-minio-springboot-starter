package com.zsq.winter.minio.enums;

import com.amazonaws.services.s3.model.Bucket;
import lombok.Getter;

@Getter
public enum PolicyType {
    READ_ONLY("只读", "{\n    \"Version\": \"2012-10-17\",\n    \"Statement\": [\n        {\n            \"Effect\": \"Allow\",\n            \"Principal\": {\n                \"AWS\": [\n                    \"*\"\n                ]\n            },\n            \"Action\": [\n                \"s3:GetBucketLocation\"\n            ],\n            \"Resource\": [\n                \"arn:aws:s3:::amazonBucketName\"\n            ]\n        },\n        {\n            \"Effect\": \"Allow\",\n            \"Principal\": {\n                \"AWS\": [\n                    \"*\"\n                ]\n            },\n            \"Action\": [\n                \"s3:ListBucket\"\n            ],\n            \"Resource\": [\n                \"arn:aws:s3:::amazonBucketName\"\n            ],\n            \"Condition\": {\n                \"StringEquals\": {\n                    \"s3:prefix\": [\n                        \"*\"\n                    ]\n                }\n            }\n        },\n        {\n            \"Effect\": \"Allow\",\n            \"Principal\": {\n                \"AWS\": [\n                    \"*\"\n                ]\n            },\n            \"Action\": [\n                \"s3:GetObject\"\n            ],\n            \"Resource\": [\n                \"arn:aws:s3:::amazonBucketName/**\"\n            ]\n        }\n    ]\n}"),
    WRITE_ONLY("只写", "{\n    \"Version\": \"2012-10-17\",\n    \"Statement\": [\n        {\n            \"Effect\": \"Allow\",\n            \"Principal\": {\n                \"AWS\": [\n                    \"*\"\n                ]\n            },\n            \"Action\": [\n                \"s3:GetBucketLocation\",\n                \"s3:ListBucketMultipartUploads\"\n            ],\n            \"Resource\": [\n                \"arn:aws:s3:::amazonBucketName\"\n            ]\n        },\n        {\n            \"Effect\": \"Allow\",\n            \"Principal\": {\n                \"AWS\": [\n                    \"*\"\n                ]\n            },\n            \"Action\": [\n                \"s3:PutObject\",\n                \"s3:AbortMultipartUpload\",\n                \"s3:DeleteObject\",\n                \"s3:ListMultipartUploadParts\"\n            ],\n            \"Resource\": [\n                \"arn:aws:s3:::amazonBucketName/**\"\n            ]\n        }\n    ]\n}"),
    READ_WRITE("可读可写", "{\n    \"Version\": \"2012-10-17\",\n    \"Statement\": [\n        {\n            \"Effect\": \"Allow\",\n            \"Principal\": {\n                \"AWS\": [\n                    \"*\"\n                ]\n            },\n            \"Action\": [\n                \"s3:GetBucketLocation\",\n                \"s3:ListBucketMultipartUploads\"\n            ],\n            \"Resource\": [\n                \"arn:aws:s3:::amazonBucketName\"\n            ]\n        },\n        {\n            \"Effect\": \"Allow\",\n            \"Principal\": {\n                \"AWS\": [\n                    \"*\"\n                ]\n            },\n            \"Action\": [\n                \"s3:ListBucket\"\n            ],\n            \"Resource\": [\n                \"arn:aws:s3:::amazonBucketName\"\n            ],\n            \"Condition\": {\n                \"StringEquals\": {\n                    \"s3:prefix\": [\n                        \"*\"\n                    ]\n                }\n            }\n        },\n        {\n            \"Effect\": \"Allow\",\n            \"Principal\": {\n                \"AWS\": [\n                    \"*\"\n                ]\n            },\n            \"Action\": [\n                \"s3:ListMultipartUploadParts\",\n                \"s3:PutObject\",\n                \"s3:AbortMultipartUpload\",\n                \"s3:DeleteObject\",\n                \"s3:GetObject\"\n            ],\n            \"Resource\": [\n                \"arn:aws:s3:::amazonBucketName/**\"\n            ]\n        }\n    ]\n}");

    private final String desc;
    private final String policy;
    // 这个是策略里面存储桶的名称，给指定的存储桶设定存储策略
    private static final String AMAZON_BUCKET_NAME = "amazonBucketName";

    private PolicyType(String desc, String policy) {
        this.desc = desc;
        this.policy = policy;
    }

    /**
     * 将策略里面默认的存储桶名称替换成指定的存储桶
     *
     * @param policyType 策略类型
     * @param bucketName bucket名称
     * @return {@link String}
     */
    public static String getPolicy(PolicyType policyType, String bucketName) {
        return policyType.getPolicy().replace(AMAZON_BUCKET_NAME, bucketName);
    }

}
