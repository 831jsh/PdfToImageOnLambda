package com.serverless;

import java.util.concurrent.Future;

import com.amazonaws.handlers.AsyncHandler;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.lambda.AWSLambdaAsync;
import com.amazonaws.services.lambda.AWSLambdaAsyncClientBuilder;
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
    private int chance = 3;

    public RequestConvert(String bucket, String key, int pageNum, String pageId, String extension) {
        this.bucket = bucket;
        this.key = key;
        this.pageNum = pageNum;
        this.pageId = pageId;
        this.extension = extension;
    }

    public Future<InvokeResult> invoke() {
        AWSLambdaAsyncClientBuilder builder = AWSLambdaAsyncClientBuilder.standard()
                .withRegion(Regions.fromName("ap-northeast-2"));
        AWSLambdaAsync client = builder.build();
        String payload = getPayload();
        InvokeRequest request = new InvokeRequest().withFunctionName("pdf2image-dev-convertPdfToImg")
                .withPayload(payload);
        Future<InvokeResult> invoke = client.invokeAsync(request, new AsyncLambdaHandler());
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

    private class AsyncLambdaHandler implements AsyncHandler<InvokeRequest, InvokeResult> {
        public void onSuccess(InvokeRequest req, InvokeResult res) {

        }

        public void onError(Exception e) {
            if (chance > 0) {
                chance--;
                invoke();
            }
        }
    }
}