package com.zsq.winter.minio.config;

import com.amazonaws.regions.Regions;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "winter-aws")
public class AmazonS3Properties {


    /**
     * 地域节点(物理服务器地址)
     */
    private String endpoint;

    /**
     * Access key就像用户ID，可以唯一标识你的账户
     */
    private String accessKey;

    /**
     * Secret key是你账户的密码
     */
    private String secretKey;

    /**
     * 存储桶名称
     */
    private String bucket;

    /**
     * 区域(默为ap-east-1)
     */
    private String region= Regions.AP_EAST_1.name();
    /**
     * 自定义域名，配置此参数时，返回url优先使用
     */
    private String customDomain;

    /**
     * true path-style nginx 反向代理和S3默认支持 pathStyle模式 {<a href="http://endpoint/bucketname">...</a>} <a href="http://s3.amazonaws.com/bucket-name/object-key">...</a>。
     * false supports virtual-hosted-style 阿里云等需要配置为 virtual-hosted-style 模式{<a href="http://bucketname.endpoint">...</a>}  <a href="http://s3.amazonaws.com/bucket-name/object-key">...</a>
     * 只是url的显示不一样
     */
    private Boolean pathStyleAccess = true;

    /**
     * 是否启用，默认为：true，不填该属性或者为false不生效
     */
    private Boolean enabled = true;
}