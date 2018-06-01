package com.serverless;

import com.amazonaws.regions.Regions;
import com.amazonaws.services.lambda.AWSLambda;
import com.amazonaws.services.lambda.AWSLambdaClientBuilder;
import com.amazonaws.services.lambda.model.InvokeRequest;
import com.amazonaws.services.lambda.model.InvokeResult;
import com.google.gson.Gson;
import com.google.gson.JsonObject;

public class RequestConvert {
    private String bucket;
    private String key;
    private int pageNum;
    private String pageId;
    private String extension;

    public RequestConvert(String bucket, String key, int pageNum, String pageId, String extension) {
        this.bucket = bucket;
        this.key = key;
        this.pageNum = pageNum;
        this.pageId = pageId;
        this.extension = extension;
    }

    public InvokeResult invoke() {
        AWSLambdaClientBuilder builder = AWSLambdaClientBuilder.standard()
                .withRegion(Regions.fromName("ap-northeast-2"));
        AWSLambda client = builder.build();
        String payload = getPayload();
        InvokeRequest request = new InvokeRequest().withFunctionName("pdf2image-dev-convertPdfToImg")
                .withPayload(payload);
        InvokeResult invoke = client.invoke(request);
        return invoke;
    }

    private String getPayload() {
        Gson gson = new Gson();
        JsonObject jsonObject = new JsonObject();
        jsonObject.addProperty("eventName", "s3PutImageFromPdfPage");
        jsonObject.addProperty("bucketName", bucket);
        jsonObject.addProperty("key", key);
        jsonObject.addProperty("pageNum", pageNum);
        jsonObject.addProperty("pageId", pageId);
        jsonObject.addProperty("extension", extension);

        return gson.toJson(jsonObject);
    }
}