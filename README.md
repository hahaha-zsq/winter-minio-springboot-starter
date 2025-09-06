# 🌨️ Winter MinIO Spring Boot Starter

<div align="center">

[![License](https://img.shields.io/badge/license-Apache%202-4EB1BA.svg)](https://www.apache.org/licenses/LICENSE-2.0.html)
[![Java support](https://img.shields.io/badge/Java-1.8+-green.svg)](https://openjdk.java.net/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-2.6+-blue.svg)](https://spring.io/projects/spring-boot)
[![AWS SDK S3](https://img.shields.io/badge/AWS%20SDK%20S3-1.12.709-FF9900.svg)](https://aws.amazon.com/sdk-for-java/)
[![Maven Central](https://img.shields.io/badge/Maven%20Central-0.0.1-blue.svg)](https://search.maven.org/)
[![GitHub stars](https://img.shields.io/github/stars/hahaha-zsq/winter-minio-spring-boot-starter.svg?style=social&label=Stars)](https://github.com/hahaha-zsq/winter-minio-spring-boot-starter)

</div>

> 🚀 **企业级 S3 兼容对象存储 Spring Boot Starter**  
> 基于 AWS S3 SDK 构建，完美兼容 MinIO、阿里云 OSS、腾讯云 COS 等 S3 协议存储服务。提供开箱即用的 `AmazonS3Template` 工具类，封装了存储桶管理、对象上传/下载、预签名 URL、分片上传、断点续传、进度监控、元数据管理、生命周期、版本控制、通知配置等企业级功能。

## 🎯 项目特色

- **🔧 开箱即用**：零配置启动，自动装配 S3 客户端和模板类
- **🌐 广泛兼容**：支持 MinIO、AWS S3、阿里云 OSS、腾讯云 COS 等 S3 协议存储
- **⚡ 性能优化**：内置连接池、超时配置、重试机制，支持大文件分块上传
- **🔒 安全可靠**：预签名 URL、访问策略管理、SSL/TLS 支持
- **📊 功能完整**：涵盖存储桶管理、对象操作、元数据、生命周期、版本控制等全场景
- **🎨 API 友好**：简洁直观的 API 设计，支持链式调用和默认参数
- **📈 企业级**：支持断点续传、进度监控、批量操作、异常处理

## 📋 目录

- [🎯 项目特色](#-项目特色)
- [🚀 核心功能](#-核心功能)
- [📦 快速开始](#-快速开始)
- [⚙️ 配置详解](#️-配置详解)
- [💡 使用示例](#-使用示例)
- [📚 完整 API 文档](#-完整-api-文档)
- [🏗️ 架构设计](#️-架构设计)
- [🔧 最佳实践](#-最佳实践)
- [❓ 常见问题](#-常见问题)
- [🤝 贡献指南](#-贡献指南)

## 🚀 核心功能

### 📁 存储桶管理
- **桶操作**：创建、删除、检查存在性、列举所有桶
- **访问策略**：内置只读、只写、读写策略，支持自定义 JSON 策略
- **生命周期**：配置对象自动删除、转换存储类型规则
- **版本控制**：启用/禁用对象版本管理，列举历史版本
- **事件通知**：配置桶事件通知（SQS、SNS、Lambda）

### 📄 对象操作
- **上传功能**：支持文件、InputStream、MultipartFile 上传
- **下载功能**：支持文件下载、流式读取、范围下载、断点续传
- **进度监控**：实时上传/下载进度回调
- **元数据管理**：设置/获取对象元数据、Content-Type、自定义标签
- **对象管理**：复制、移动、重命名、删除、批量操作

### 🔐 安全与访问控制
- **预签名 URL**：生成临时访问链接，支持 GET/PUT/DELETE 操作
- **访问策略**：桶级别和对象级别权限控制
- **SSL/TLS**：支持 HTTPS 加密传输
- **自定义域名**：支持 CDN 和自定义域名访问

### ⚡ 性能优化
- **分片上传**：大文件自动分片，支持并发上传
- **连接池**：内置连接池管理，支持高并发
- **重试机制**：自动重试失败请求
- **超时配置**：灵活的连接和读取超时设置

---

## 📦 快速开始

### 1. 添加依赖

**Maven**
```xml
<dependency>
    <groupId>io.github.hahaha-zsq</groupId>
    <artifactId>winter-minio-spring-boot-starter</artifactId>
    <version>0.0.1</version>
</dependency>
```

**Gradle**
```gradle
implementation 'io.github.hahaha-zsq:winter-minio-spring-boot-starter:0.0.1'
```

### 2. 基础配置

**application.yml**
```yaml
winter-aws:
  # 必填配置
  access-key: minioadmin              # 访问密钥
  secret-key: minioadmin              # 秘密密钥
  endpoint: http://localhost:9000     # 服务端点
  bucket: default-bucket              # 默认存储桶
  
  # 可选配置
  region: us-east-1                   # 区域设置
  path-style-access: true             # 路径风格访问（MinIO推荐true）
  custom-domain: https://cdn.example.com  # 自定义域名
  enabled: true                       # 是否启用（默认true）
```

**application.properties**
```properties
# 必填配置
winter-aws.access-key=minioadmin
winter-aws.secret-key=minioadmin
winter-aws.endpoint=http://localhost:9000
winter-aws.bucket=default-bucket

# 可选配置
winter-aws.region=us-east-1
winter-aws.path-style-access=true
winter-aws.custom-domain=https://cdn.example.com
winter-aws.enabled=true
```

### 3. 注入使用

```java
@RestController
@RequiredArgsConstructor
public class FileController {
    
    private final AmazonS3Template s3Template;
    
    @PostMapping("/upload")
    public ResponseEntity<String> uploadFile(@RequestParam("file") MultipartFile file) {
        try {
            String objectKey = "uploads/" + System.currentTimeMillis() + "/" + file.getOriginalFilename();
            s3Template.putObject(objectKey, file, null);
            String fileUrl = s3Template.getGatewayUrl(objectKey);
            return ResponseEntity.ok(fileUrl);
        } catch (IOException e) {
            return ResponseEntity.status(500).body("上传失败: " + e.getMessage());
        }
    }
    
    @GetMapping("/download/{objectKey}")
    public ResponseEntity<String> getDownloadUrl(@PathVariable String objectKey) {
        // 生成15分钟有效期的下载链接
        String downloadUrl = s3Template.getObjectUrl(objectKey, 15);
        return ResponseEntity.ok(downloadUrl);
    }
}
```

---

## ⚙️ 配置详解

### 配置参数说明

| 参数 | 类型 | 必填 | 默认值 | 说明 |
|------|------|------|--------|------|
| `access-key` | String | ✅ | - | S3 访问密钥 ID |
| `secret-key` | String | ✅ | - | S3 秘密访问密钥 |
| `endpoint` | String | ✅ | - | S3 服务端点 URL |
| `bucket` | String | ✅ | - | 默认存储桶名称 |
| `region` | String | ❌ | `ap-east-1` | AWS 区域代码 |
| `path-style-access` | Boolean | ❌ | `true` | 是否使用路径风格访问 |
| `custom-domain` | String | ❌ | - | 自定义访问域名 |
| `enabled` | Boolean | ❌ | `true` | 是否启用自动配置 |

### 不同存储服务配置示例

**MinIO 配置**
```yaml
winter-aws:
  access-key: minioadmin
  secret-key: minioadmin
  endpoint: http://localhost:9000
  bucket: my-bucket
  path-style-access: true
  region: us-east-1
```

**AWS S3 配置**
```yaml
winter-aws:
  access-key: AKIAIOSFODNN7EXAMPLE
  secret-key: wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY
  endpoint: https://s3.amazonaws.com
  bucket: my-s3-bucket
  path-style-access: false
  region: us-west-2
```

**阿里云 OSS 配置**
```yaml
winter-aws:
  access-key: your-access-key-id
  secret-key: your-access-key-secret
  endpoint: https://oss-cn-hangzhou.aliyuncs.com
  bucket: my-oss-bucket
  path-style-access: false
  region: oss-cn-hangzhou
```

**腾讯云 COS 配置**
```yaml
winter-aws:
  access-key: your-secret-id
  secret-key: your-secret-key
  endpoint: https://cos.ap-beijing.myqcloud.com
  bucket: my-cos-bucket
  path-style-access: false
  region: ap-beijing
```

### 高级配置

项目内置了优化的客户端配置，包括：

- **连接池**：最大连接数 500
- **超时设置**：连接超时 10s，Socket 超时 20s
- **重试机制**：最大重试 2 次
- **协议**：默认 HTTP（生产环境建议 HTTPS）

---

## 💡 使用示例

### 基础文件操作

```java
@Service
@RequiredArgsConstructor
public class FileService {
    
    private final AmazonS3Template s3Template;
    
    /**
     * 上传文件
     */
    public String uploadFile(MultipartFile file, String folder) throws IOException {
        // 生成唯一文件名
        String fileName = folder + "/" + System.currentTimeMillis() + "_" + file.getOriginalFilename();
        
        // 上传文件
        s3Template.putObject(fileName, file, null);
        
        // 返回访问URL
        return s3Template.getGatewayUrl(fileName);
    }
    
    /**
     * 下载文件到本地
     */
    public void downloadFile(String objectKey, String localPath) throws IOException {
        File localFile = new File(localPath);
        s3Template.downloadObject(objectKey, localFile);
    }
    
    /**
     * 获取文件流
     */
    public InputStream getFileStream(String objectKey) {
        return s3Template.getObjectInputStream(objectKey);
    }
    
    /**
     * 删除文件
     */
    public void deleteFile(String objectKey) {
        s3Template.removeObject(objectKey);
    }
    
    /**
     * 批量删除文件
     */
    public void deleteFiles(List<String> objectKeys) {
        s3Template.deleteObjects(objectKeys);
    }
}
```

### 预签名 URL 操作

```java
@RestController
@RequiredArgsConstructor
public class PresignedUrlController {
    
    private final AmazonS3Template s3Template;
    
    /**
     * 生成上传预签名URL
     */
    @PostMapping("/presigned-upload-url")
    public ResponseEntity<String> generateUploadUrl(
            @RequestParam String fileName,
            @RequestParam(defaultValue = "60") Integer expireMinutes) {
        
        String uploadUrl = s3Template.getPresignedObjectPutUrl(
            fileName, expireMinutes, TimeUnit.MINUTES);
        
        return ResponseEntity.ok(uploadUrl);
    }
    
    /**
     * 生成下载预签名URL
     */
    @GetMapping("/presigned-download-url")
    public ResponseEntity<String> generateDownloadUrl(
            @RequestParam String objectKey,
            @RequestParam(defaultValue = "15") Integer expireMinutes) {
        
        String downloadUrl = s3Template.getObjectUrl(
            objectKey, expireMinutes, TimeUnit.MINUTES);
        
        return ResponseEntity.ok(downloadUrl);
    }
}
```

### 大文件分片上传

```java
@Service
@RequiredArgsConstructor
public class LargeFileUploadService {
    
    private final AmazonS3Template s3Template;
    
    /**
     * 大文件分片上传
     */
    public String uploadLargeFile(MultipartFile file, String objectKey) throws Exception {
        // 1. 初始化分片上传
        InitiateMultipartUploadResult initResult = s3Template.initiateMultipartUpload(
            objectKey, file.getContentType());
        String uploadId = initResult.getUploadId();
        
        try {
            List<PartETag> partETags = new ArrayList<>();
            
            // 2. 分片上传（每片5MB）
            long partSize = 5 * 1024 * 1024; // 5MB
            long fileSize = file.getSize();
            int partNumber = 1;
            
            try (InputStream inputStream = file.getInputStream()) {
                for (long position = 0; position < fileSize; position += partSize) {
                    long currentPartSize = Math.min(partSize, fileSize - position);
                    
                    // 创建分片数据
                    byte[] partData = new byte[(int) currentPartSize];
                    inputStream.read(partData);
                    
                    // 上传分片
                    UploadPartResult uploadResult = s3Template.uploadPart(
                        uploadId, objectKey, null, partNumber, 
                        currentPartSize, new ByteArrayInputStream(partData));
                    
                    partETags.add(uploadResult.getPartETag());
                    partNumber++;
                }
            }
            
            // 3. 完成分片上传
            s3Template.completeMultipartUpload(objectKey, uploadId);
            
            return s3Template.getGatewayUrl(objectKey);
            
        } catch (Exception e) {
            // 失败时中止上传
            s3Template.abortMultipartUpload(objectKey, uploadId);
            throw e;
        }
    }
}
```

### 存储桶管理

```java
@Service
@RequiredArgsConstructor
public class BucketManagementService {
    
    private final AmazonS3Template s3Template;
    
    /**
     * 创建存储桶并设置策略
     */
    public void createBucketWithPolicy(String bucketName, PolicyType policyType) {
        // 创建存储桶
        boolean created = s3Template.createBucket(bucketName);
        
        if (created) {
            // 设置访问策略
            s3Template.setBucketPolicy(bucketName, policyType);
        }
    }
    
    /**
     * 列举所有存储桶
     */
    public List<String> listAllBuckets() {
        return s3Template.getAllBuckets().stream()
            .map(Bucket::getName)
            .collect(Collectors.toList());
    }
    
    /**
     * 启用版本控制
     */
    public void enableVersioning(String bucketName) {
        s3Template.enableBucketVersioning(bucketName);
    }
    
    /**
     * 设置生命周期规则
     */
    public void setLifecycleRule(String bucketName) {
        BucketLifecycleConfiguration config = new BucketLifecycleConfiguration();
        
        BucketLifecycleConfiguration.Rule rule = new BucketLifecycleConfiguration.Rule()
            .withId("DeleteOldFiles")
            .withStatus(BucketLifecycleConfiguration.ENABLED)
            .withExpirationInDays(30); // 30天后删除
        
        config.setRules(Arrays.asList(rule));
        s3Template.setBucketLifecycleConfiguration(bucketName, config);
    }
}
```

---

## 4️⃣ 常见场景示例

- 🪣 创建存储桶并设置策略
```java
amazonS3Template.createBucket("logs");
amazonS3Template.setBucketPolicy("logs", com.zsq.winter.minio.enums.PolicyType.READ_ONLY);
```

- 📄 获取对象元数据、大小、最后修改时间
```java
ObjectMetadata md = amazonS3Template.getObjectMetadata("key");
long size = amazonS3Template.getObjectSize("key");
Date last = amazonS3Template.getObjectLastModified("key");
```

- 🔁 复制、重命名、删除、批量删除
```java
amazonS3Template.copyObject("old/key.jpg", "copy/key.jpg");
amazonS3Template.renameObject("old/name.jpg", "new/name.jpg");
amazonS3Template.removeObject("to/delete.jpg");
amazonS3Template.deleteObjects(Arrays.asList("a.jpg","b.jpg"));
```

- ⏬ 下载到文件 / ⏯️ 范围读取
```java
amazonS3Template.downloadObject("key", new File("/tmp/file.bin"));
InputStream range = amazonS3Template.getObjectInputStream("key", 0, 1024);
```

- 🌐 获取网关 URL（支持自定义域名或自动拼接 Endpoint）
```java
String publicUrl = amazonS3Template.getGatewayUrl("path/to/img.png");
```

---

## 📚 完整 API 参考

### 🗂️ 存储桶管理 API

#### 基础操作

| 方法签名 | 返回类型 | 功能描述 |
|---------|---------|----------|
| `bucketExists()` | `boolean` | 检查默认存储桶是否存在 |
| `bucketExists(String bucketName)` | `boolean` | 检查指定存储桶是否存在 |
| `createBucket()` | `boolean` | 创建默认存储桶 |
| `createBucket(String bucketName)` | `boolean` | 创建指定名称的存储桶 |
| `createBucket(String bucketName, String policyText)` | `boolean` | 创建存储桶并设置自定义策略 |
| `createBucket(String bucketName, PolicyType policyType)` | `boolean` | 创建存储桶并设置预定义策略 |
| `removeBucket()` | `boolean` | 删除默认存储桶 |
| `removeBucket(String bucketName)` | `boolean` | 删除指定存储桶 |
| `getAllBuckets()` | `List<Bucket>` | 获取所有存储桶列表 |
| `getBucket(String bucketName)` | `Optional<Bucket>` | 获取指定存储桶信息 |

#### 策略与权限

| 方法签名 | 返回类型 | 功能描述 |
|---------|---------|----------|
| `setBucketPolicy(PolicyType policyType)` | `void` | 为默认存储桶设置策略 |
| `setBucketPolicy(String bucketName, PolicyType policyType)` | `void` | 为指定存储桶设置预定义策略 |
| `setBucketPolicy(String bucketName, String policyText)` | `void` | 为指定存储桶设置自定义策略 |

**策略类型枚举：**
- `PolicyType.READ_ONLY` - 只读访问
- `PolicyType.WRITE_ONLY` - 只写访问  
- `PolicyType.READ_WRITE` - 读写访问

#### 版本控制

| 方法签名 | 返回类型 | 功能描述 |
|---------|---------|----------|
| `enableBucketVersioning()` | `void` | 启用默认存储桶版本控制 |
| `enableBucketVersioning(String bucketName)` | `void` | 启用指定存储桶版本控制 |
| `suspendBucketVersioning(String bucketName)` | `void` | 暂停指定存储桶版本控制 |
| `listVersions(String bucketName, String prefix)` | `VersionListing` | 列举对象版本 |

#### 生命周期管理

| 方法签名 | 返回类型 | 功能描述 |
|---------|---------|----------|
| `setBucketLifecycleConfiguration(String bucketName, BucketLifecycleConfiguration config)` | `void` | 设置存储桶生命周期配置 |
| `getBucketLifecycleConfiguration(String bucketName)` | `BucketLifecycleConfiguration` | 获取存储桶生命周期配置 |
| `deleteBucketLifecycleConfiguration(String bucketName)` | `void` | 删除存储桶生命周期配置 |

#### 通知配置

| 方法签名 | 返回类型 | 功能描述 |
|---------|---------|----------|
| `setBucketNotificationConfiguration(String bucketName, BucketNotificationConfiguration config)` | `void` | 设置存储桶事件通知配置 |
| `getBucketNotificationConfiguration(String bucketName)` | `BucketNotificationConfiguration` | 获取存储桶事件通知配置 |

---

### 📁 对象操作 API

#### 文件上传

| 方法签名 | 返回类型 | 功能描述 |
|---------|---------|----------|
| `putObject(String objectKey, MultipartFile file, Map<String, String> metadata)` | `PutObjectResult` | 上传 MultipartFile 到默认存储桶 |
| `putObject(String bucketName, String objectKey, MultipartFile file, Map<String, String> metadata)` | `PutObjectResult` | 上传 MultipartFile 到指定存储桶 |
| `putObject(String bucketName, String objectKey, String contentType, InputStream inputStream, long contentLength)` | `PutObjectResult` | 通过 InputStream 上传（推荐大文件） |
| `putObject(String bucketName, String objectKey, String contentType, InputStream inputStream, long contentLength, int maxReadLength)` | `PutObjectResult` | 通过 InputStream 上传并限制读取长度 |
| `putObject(String objectKey, byte[] data, String contentType)` | `PutObjectResult` | 上传字节数组到默认存储桶 |
| `putObject(String bucketName, String objectKey, byte[] data, String contentType)` | `PutObjectResult` | 上传字节数组到指定存储桶 |

#### 文件下载

| 方法签名 | 返回类型 | 功能描述 |
|---------|---------|----------|
| `getObjectInputStream(String objectKey)` | `InputStream` | 获取默认存储桶对象输入流 |
| `getObjectInputStream(String bucketName, String objectKey)` | `InputStream` | 获取指定存储桶对象输入流 |
| `getObjectInputStream(String bucketName, String objectKey, long start, long end)` | `InputStream` | 获取对象指定范围的输入流 |
| `downloadObject(String objectKey, File localFile)` | `void` | 下载对象到本地文件（默认存储桶） |
| `downloadObject(String bucketName, String objectKey, File localFile)` | `void` | 下载对象到本地文件（指定存储桶） |
| `downloadLargeObject(String objectKey, File localFile)` | `void` | 大文件分块下载（默认存储桶） |
| `downloadLargeObject(String objectKey, File localFile, long chunkSize)` | `void` | 大文件分块下载并指定块大小 |
| `downloadLargeObject(String bucketName, String objectKey, File localFile, long chunkSize)` | `void` | 大文件分块下载（指定存储桶和块大小） |
| `downloadObjectWithResume(String objectKey, File localFile)` | `void` | 断点续传下载（默认存储桶） |
| `downloadObjectWithResume(String objectKey, File localFile, long chunkSize)` | `void` | 断点续传下载并指定块大小 |
| `downloadObjectWithResume(String bucketName, String objectKey, File localFile, long chunkSize)` | `void` | 断点续传下载（指定存储桶和块大小） |
| `downloadObjectWithProgress(String objectKey, File localFile, BiConsumer<Long, Long> progressCallback)` | `void` | 带进度监控的下载（默认存储桶） |
| `downloadObjectWithProgress(String bucketName, String objectKey, File localFile, BiConsumer<Long, Long> progressCallback)` | `void` | 带进度监控的下载（指定存储桶） |

#### 对象信息

| 方法签名 | 返回类型 | 功能描述 |
|---------|---------|----------|
| `objectExists(String objectKey)` | `boolean` | 检查对象是否存在（默认存储桶） |
| `objectExists(String bucketName, String objectKey)` | `boolean` | 检查对象是否存在（指定存储桶） |
| `getObjectInfo(String objectKey)` | `S3Object` | 获取对象信息（默认存储桶） |
| `getObjectInfo(String bucketName, String objectKey)` | `S3Object` | 获取对象信息（指定存储桶） |
| `getObjectMetadata(String objectKey)` | `ObjectMetadata` | 获取对象元数据（默认存储桶） |
| `getObjectMetadata(String bucketName, String objectKey)` | `ObjectMetadata` | 获取对象元数据（指定存储桶） |
| `getObjectSize(String objectKey)` | `long` | 获取对象大小（默认存储桶） |
| `getObjectSize(String bucketName, String objectKey)` | `long` | 获取对象大小（指定存储桶） |
| `getObjectLastModified(String objectKey)` | `Date` | 获取对象最后修改时间（默认存储桶） |
| `getObjectLastModified(String bucketName, String objectKey)` | `Date` | 获取对象最后修改时间（指定存储桶） |

#### 对象管理

| 方法签名 | 返回类型 | 功能描述 |
|---------|---------|----------|
| `copyObject(String sourceKey, String targetKey)` | `CopyObjectResult` | 在默认存储桶内复制对象 |
| `copyObject(String sourceBucket, String sourceKey, String targetBucket, String targetKey)` | `CopyObjectResult` | 跨存储桶复制对象 |
| `renameObject(String oldKey, String newKey)` | `void` | 重命名对象（默认存储桶） |
| `renameObject(String bucketName, String oldKey, String newKey)` | `void` | 重命名对象（指定存储桶） |
| `removeObject(String objectKey)` | `void` | 删除对象（默认存储桶） |
| `removeObject(String bucketName, String objectKey)` | `void` | 删除对象（指定存储桶） |
| `deleteObjects(List<String> objectKeys)` | `DeleteObjectsResult` | 批量删除对象（默认存储桶） |
| `deleteObjects(String bucketName, List<String> objectKeys)` | `DeleteObjectsResult` | 批量删除对象（指定存储桶） |
| `setObjectMetadata(String objectKey, ObjectMetadata metadata)` | `void` | 设置对象元数据（默认存储桶） |
| `setObjectMetadata(String bucketName, String objectKey, ObjectMetadata metadata)` | `void` | 设置对象元数据（指定存储桶） |

---

### 🔗 预签名 URL API

#### 下载 URL

| 方法签名 | 返回类型 | 功能描述 |
|---------|---------|----------|
| `getObjectUrl(String objectKey)` | `String` | 生成默认过期时间的下载 URL（默认存储桶） |
| `getObjectUrl(String objectKey, int expires)` | `String` | 生成指定过期时间的下载 URL（默认存储桶） |
| `getObjectUrl(String objectKey, int expires, TimeUnit timeUnit)` | `String` | 生成指定过期时间和时间单位的下载 URL（默认存储桶） |
| `getObjectUrl(String bucketName, String objectKey, int expires, TimeUnit timeUnit)` | `String` | 生成指定过期时间的下载 URL（指定存储桶） |
| `getObjectUrl(String bucketName, String objectKey, int expires, TimeUnit timeUnit, String contentType)` | `String` | 生成指定过期时间和内容类型的下载 URL（指定存储桶） |

#### 上传 URL

| 方法签名 | 返回类型 | 功能描述 |
|---------|---------|----------|
| `getPresignedObjectPutUrl(String objectKey, int expires, TimeUnit timeUnit)` | `String` | 生成上传预签名 URL（默认存储桶） |
| `getPresignedObjectPutUrl(String bucketName, String objectKey, int expires, TimeUnit timeUnit)` | `String` | 生成上传预签名 URL（指定存储桶） |
| `getPresignedObjectPutUrl(String bucketName, String objectKey, int expires, TimeUnit timeUnit, String contentType)` | `String` | 生成上传预签名 URL 并指定内容类型（指定存储桶） |

#### 公共访问 URL

| 方法签名 | 返回类型 | 功能描述 |
|---------|---------|----------|
| `getGatewayUrl(String objectKey)` | `String` | 获取网关访问 URL（默认存储桶，支持自定义域名） |
| `getGatewayUrl(String bucketName, String objectKey)` | `String` | 获取网关访问 URL（指定存储桶，支持自定义域名） |

---

### 🧩 分片上传 API

#### 分片上传流程

| 方法签名 | 返回类型 | 功能描述 |
|---------|---------|----------|
| `initiateMultipartUpload(String objectKey, String contentType)` | `InitiateMultipartUploadResult` | 初始化分片上传（默认存储桶） |
| `initiateMultipartUpload(String bucketName, String objectKey, String contentType)` | `InitiateMultipartUploadResult` | 初始化分片上传（指定存储桶） |
| `uploadPart(String uploadId, String objectKey, String bucketName, int partNumber, MultipartFile file)` | `UploadPartResult` | 上传分片（通过 MultipartFile） |
| `uploadPart(String uploadId, String objectKey, String bucketName, int partNumber, long partSize, InputStream inputStream)` | `UploadPartResult` | 上传分片（通过输入流） |
| `uploadPart(String bucketName, String uploadId, String objectKey, String md5, Integer partNumber, long partSize, InputStream inputStream)` | `UploadPartResult` | 上传分片（带 MD5 校验） |
| `completeMultipartUpload(String objectKey, String uploadId)` | `CompleteMultipartUploadResult` | 完成分片上传（默认存储桶） |
| `completeMultipartUpload(String bucketName, String objectKey, String uploadId)` | `CompleteMultipartUploadResult` | 完成分片上传（指定存储桶） |
| `completeMultipartUpload(String bucketName, String objectKey, String uploadId, List<PartSummary> parts)` | `CompleteMultipartUploadResult` | 完成分片上传（指定分片列表） |
| `abortMultipartUpload(String objectKey, String uploadId)` | `void` | 中止分片上传（默认存储桶） |
| `abortMultipartUpload(String bucketName, String objectKey, String uploadId)` | `void` | 中止分片上传（指定存储桶） |

#### 分片管理

| 方法签名 | 返回类型 | 功能描述 |
|---------|---------|----------|
| `listParts(String bucketName, String objectKey, String uploadId)` | `PartListing` | 列举已上传的分片 |
| `listMultipartUploads(String bucketName, String prefix, String delimiter)` | `MultipartUploadListing` | 列举进行中的分片上传 |
| `getMultipartInfoArr(String bucketName, String prefix, String delimiter)` | `List<Map<String,Object>>` | 获取分片上传信息数组 |

---

### 📋 对象列举 API

#### 基础列举

| 方法签名 | 返回类型 | 功能描述 |
|---------|---------|----------|
| `getAllObjects()` | `List<S3ObjectSummary>` | 获取默认存储桶中的所有对象 |
| `getAllObjects(String bucketName)` | `List<S3ObjectSummary>` | 获取指定存储桶中的所有对象 |
| `getAllObjectsByPrefix(String prefix)` | `List<S3ObjectSummary>` | 按前缀获取默认存储桶中的对象 |
| `getAllObjectsByPrefix(String bucketName, String prefix)` | `List<S3ObjectSummary>` | 按前缀获取指定存储桶中的对象 |

#### 分页列举

| 方法签名 | 返回类型 | 功能描述 |
|---------|---------|----------|
| `listObjects(String bucketName, String prefix, int maxKeys)` | `ObjectListing` | 分页列举对象（指定最大数量） |
| `listObjects(String bucketName, String prefix, String delimiter, int maxKeys)` | `ObjectListing` | 分页列举对象（指定分隔符和最大数量） |

#### 递归列举

| 方法签名 | 返回类型 | 功能描述 |
|---------|---------|----------|
| `listObjectsRecursively(String bucketName, String prefix)` | `List<S3ObjectSummary>` | 递归列举对象（包含子目录） |

---

### 🔧 工具方法 API

#### URL 处理

| 方法签名 | 返回类型 | 功能描述 |
|---------|---------|----------|
| `urlEncode(String url)` | `String` | URL 编码 |
| `urlDecode(String encodedUrl)` | `String` | URL 解码 |

#### 文件类型检测

| 方法签名 | 返回类型 | 功能描述 |
|---------|---------|----------|
| `getContentType(String fileName)` | `String` | 根据文件名获取 MIME 类型 |
| `getContentType(File file)` | `String` | 根据文件对象获取 MIME 类型 |

#### 数据验证

| 方法签名 | 返回类型 | 功能描述 |
|---------|---------|----------|
| `validateETag(String objectKey, String expectedETag)` | `boolean` | 验证对象 ETag（默认存储桶） |
| `validateETag(String bucketName, String objectKey, String expectedETag)` | `boolean` | 验证对象 ETag（指定存储桶） |

---

## ⚠️ 注意事项与最佳实践

### 🔧 配置相关

#### 路径风格访问
- **MinIO**：必须设置 `path-style-access: true`
- **AWS S3**：建议设置 `path-style-access: false`
- **阿里云 OSS/腾讯云 COS**：设置 `path-style-access: false`

#### 区域配置
```yaml
# 不同服务的区域配置示例
winter-aws:
  region: us-east-1        # AWS S3
  region: oss-cn-hangzhou  # 阿里云 OSS
  region: ap-beijing       # 腾讯云 COS
  region: us-east-1        # MinIO（任意值）
```

#### 自定义域名
```yaml
winter-aws:
  custom-domain: https://cdn.example.com  # CDN 域名
  # 或
  custom-domain: https://files.example.com # 自定义域名
```

### 📁 文件操作最佳实践

#### InputStream 上传注意事项
```java
// ❌ 错误做法 - 不要依赖 available()
InputStream inputStream = file.getInputStream();
long wrongLength = inputStream.available(); // 可能不准确

// ✅ 正确做法 - 使用准确的文件大小
long correctLength = file.getSize(); // MultipartFile
// 或
long correctLength = Files.size(Paths.get(filePath)); // 本地文件

s3Template.putObject(bucketName, objectKey, contentType, inputStream, correctLength);
```

#### 大文件处理
```java
// 对于大文件（>100MB），推荐使用分片上传
if (file.getSize() > 100 * 1024 * 1024) {
    // 使用分片上传
    uploadLargeFile(file, objectKey);
} else {
    // 普通上传
    s3Template.putObject(objectKey, file, null);
}
```

#### 文件类型检测
```java
// 自动检测 Content-Type
String contentType = s3Template.getContentType(file.getOriginalFilename());
s3Template.putObject(objectKey, file.getInputStream(), contentType, file.getSize());
```

### 🔐 安全相关

#### 预签名 URL 安全
```java
// ✅ 设置合理的过期时间
String uploadUrl = s3Template.getPresignedObjectPutUrl(
    objectKey, 15, TimeUnit.MINUTES); // 15分钟过期

// ✅ 指定 Content-Type 限制上传文件类型
String restrictedUrl = s3Template.getPresignedObjectPutUrl(
    bucketName, objectKey, 15, TimeUnit.MINUTES, "image/jpeg");
```

#### 存储桶策略
```java
// 生产环境建议使用只读策略
s3Template.setBucketPolicy(bucketName, PolicyType.READ_ONLY);

// 需要公共写入时使用读写策略（谨慎使用）
s3Template.setBucketPolicy(bucketName, PolicyType.READ_WRITE);
```

### 🚀 性能优化

#### 连接池配置
项目已内置优化配置：
- 最大连接数：500
- 连接超时：10秒
- Socket 超时：20秒
- 最大重试：2次

#### 分片上传优化
```java
// 推荐分片大小：5MB - 100MB
long optimalChunkSize = Math.max(5 * 1024 * 1024, fileSize / 100);
optimalChunkSize = Math.min(optimalChunkSize, 100 * 1024 * 1024);

s3Template.downloadLargeObject(objectKey, localFile, optimalChunkSize);
```

#### 批量操作
```java
// ✅ 批量删除比单个删除效率更高
List<String> keysToDelete = Arrays.asList("file1.jpg", "file2.jpg", "file3.jpg");
s3Template.deleteObjects(keysToDelete);

// ❌ 避免循环单个删除
for (String key : keysToDelete) {
    s3Template.removeObject(key); // 效率较低
}
```

### 🛠️ 错误处理

#### 异常处理示例
```java
try {
    s3Template.putObject(objectKey, file, null);
} catch (AmazonS3Exception e) {
    if (e.getStatusCode() == 404) {
        // 存储桶不存在
        s3Template.createBucket();
        s3Template.putObject(objectKey, file, null);
    } else if (e.getStatusCode() == 403) {
        // 权限不足
        log.error("Access denied: {}", e.getMessage());
    } else {
        // 其他错误
        log.error("Upload failed: {}", e.getMessage());
    }
} catch (IOException e) {
    log.error("IO error: {}", e.getMessage());
}
```

#### 分片上传错误处理
```java
String uploadId = null;
try {
    InitiateMultipartUploadResult initResult = s3Template.initiateMultipartUpload(objectKey, contentType);
    uploadId = initResult.getUploadId();
    
    // 上传分片...
    
    s3Template.completeMultipartUpload(objectKey, uploadId);
} catch (Exception e) {
    // 失败时清理分片
    if (uploadId != null) {
        try {
            s3Template.abortMultipartUpload(objectKey, uploadId);
        } catch (Exception cleanupException) {
            log.warn("Failed to cleanup multipart upload: {}", cleanupException.getMessage());
        }
    }
    throw e;
}
```

### 📊 监控与日志

#### 下载进度监控
```java
s3Template.downloadObjectWithProgress(objectKey, localFile, (downloaded, total) -> {
    double progress = (double) downloaded / total * 100;
    log.info("Download progress: {:.2f}% ({}/{})", progress, downloaded, total);
    
    // 可以发送到前端或监控系统
    progressService.updateProgress(taskId, progress);
});
```

#### 操作日志记录
```java
@Component
public class S3OperationLogger {
    
    public void logUpload(String objectKey, long fileSize, long duration) {
        log.info("File uploaded: key={}, size={} bytes, duration={}ms", 
                objectKey, fileSize, duration);
    }
    
    public void logDownload(String objectKey, long fileSize, long duration) {
        log.info("File downloaded: key={}, size={} bytes, duration={}ms", 
                objectKey, fileSize, duration);
    }
}
```

### 🌐 CDN 集成

#### 配置 CDN 域名
```yaml
winter-aws:
  custom-domain: https://cdn.example.com
```

#### 设置缓存头
```java
// 上传时设置缓存控制
Map<String, String> metadata = new HashMap<>();
metadata.put("Cache-Control", "max-age=31536000"); // 1年缓存
metadata.put("Content-Disposition", "inline");

s3Template.putObject(objectKey, file, metadata);
```

---

## 🔄 版本兼容性

### 支持的版本
- **Spring Boot**: 2.3.x - 3.2.x
- **JDK**: 8, 11, 17, 21
- **AWS SDK**: 1.12.x
- **MinIO**: 兼容所有版本

### 版本升级指南

#### 从旧版本升级
1. 更新依赖版本
2. 检查配置文件格式
3. 测试关键功能
4. 更新代码中的废弃方法调用

#### 依赖冲突解决
```xml
<!-- 排除冲突的依赖 -->
<dependency>
    <groupId>com.zsq</groupId>
    <artifactId>winter-minio-spring-boot-starter</artifactId>
    <version>1.0.0</version>
    <exclusions>
        <exclusion>
            <groupId>com.amazonaws</groupId>
            <artifactId>aws-java-sdk-core</artifactId>
        </exclusion>
    </exclusions>
</dependency>
```

---

## 📄 许可证

本项目采用 [MIT License](LICENSE) 开源协议。

### 贡献指南

欢迎提交 Issue 和 Pull Request！

1. Fork 本仓库
2. 创建特性分支 (`git checkout -b feature/AmazingFeature`)
3. 提交更改 (`git commit -m 'Add some AmazingFeature'`)
4. 推送到分支 (`git push origin feature/AmazingFeature`)
5. 开启 Pull Request

### 问题反馈

如果您在使用过程中遇到问题，请通过以下方式反馈：

- 🐛 [提交 Bug 报告](../../issues/new?template=bug_report.md)
- 💡 [提交功能建议](../../issues/new?template=feature_request.md)
- 📖 [文档改进建议](../../issues/new?template=documentation.md)

---

## 🙏 致谢

感谢以下开源项目：

- [AWS SDK for Java](https://github.com/aws/aws-sdk-java)
- [Spring Boot](https://github.com/spring-projects/spring-boot)
- [MinIO](https://github.com/minio/minio)

以及所有贡献者的支持！