package com.serverless;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Future;

// import org.apache.log4j.Logger;

import com.amazonaws.services.lambda.model.InvokeResult;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.s3.event.S3EventNotification;
import com.amazonaws.util.json.Jackson;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.serverless.ApiGatewayResponse.Builder;

public class Handler implements RequestHandler<Map<String, Object>, ApiGatewayResponse> {
	// private static final Logger LOG = Logger.getLogger(Handler.class);

	@Override
	public ApiGatewayResponse handleRequest(Map<String, Object> input, Context context) {
		Builder responseBuilder = ApiGatewayResponse.builder();
		try {
			if (input.get("Records") != null) {
				recieveEvent(input);
				responseBuilder.setStatusCode(200).setRawBody("event ok");
			} else if (input.get("eventName").equals("convertPDFToImage")) {
				String index = recievePdf(input);
				responseBuilder.setStatusCode(200).setRawBody(index);
			} else if (input.get("eventName").equals("convertToImage")) {
				recievePage(input);
				responseBuilder.setStatusCode(200).setObjectBody("recievePage ok" + input);
			} else {
				// LOG.warn("Unknown payload");
				responseBuilder.setStatusCode(400).setRawBody("Unknown payload");
			}
		} catch (IOException e) {
			e.printStackTrace();
			responseBuilder.setStatusCode(500).setRawBody("Failed reading PDF file.");
		}

		return responseBuilder.setHeaders(Collections.singletonMap("X-Powered-By", "AWS Lambda & serverless")).build();
	}

	private String mainProcess(String bucketName, String key, String extension) throws IOException {
		PDF pdf = new PDF(new S3(bucketName).readFile(key));
		String baseURL = String.format("https://s3.%s.amazonaws.com/%s/%s", System.getenv("REGION"), bucketName, URLEncoder.encode(key, "UTF-8"));
		File indexFile = new File(String.format("/tmp/%s.json", key.replace("/", "_")));
		FileWriter osFile = new FileWriter(indexFile);

		JsonObject indexJson = new JsonObject();
		JsonArray indexPages = new JsonArray();
		indexJson.addProperty("title", new File(key).getName());
		indexJson.addProperty("defaultBackgroundImageURI", "");

		int numberOfPages = pdf.document.getNumberOfPages();

		for (int pageNum = 0; pageNum < numberOfPages; pageNum++) {
			indexPages.add(subProcess(bucketName, key, pageNum, baseURL, extension));
		}

		indexJson.add("pages", indexPages);

		Gson gson = new Gson();
		osFile.write(gson.toJson(indexJson));
		osFile.close();
		pdf.document.close();
		
		S3 s3Client = new S3(bucketName);
		s3Client.putObject(String.format("%s/index.json", key), indexFile);

		return gson.toJson(indexJson);
	}

	private JsonObject subProcess(String bucketName, String key, int pageNum, String baseURL, String extension) {
		String pageId = UUID.randomUUID().toString();
		RequestConvert request = new RequestConvert(bucketName, key, pageNum, pageId, extension);
		Future<InvokeResult> result = request.invoke();
		JsonObject indexPage = new JsonObject();
		String backgroundImageURI = String.format("%s/%d-%s.%s", baseURL, pageNum, pageId, extension);
		indexPage.addProperty("backgroundImageURI", backgroundImageURI);

		return indexPage;
	}

	private void recieveEvent(Map<String, Object> input) throws IOException {
		String inputJson = Jackson.toJsonString(input);
		String extension = "png";
		
		S3EventNotification s3 = S3EventNotification.parseJson(inputJson);
		List<S3EventNotification.S3EventNotificationRecord> records = s3.getRecords();

		for (S3EventNotification.S3EventNotificationRecord event : records) {
			String bucketName = event.getS3().getBucket().getName();
			String key = URLDecoder.decode(event.getS3().getObject().getKey(), "UTF-8");

			String indexJson = mainProcess(bucketName, key, extension);
		}
	}

	private String recievePdf(Map<String, Object> input) throws IOException {
		String bucketName = (String) input.get("bucketName");
		String key = URLDecoder.decode((String) input.get("key"), "UTF-8");
		String extension = (String) input.get("extension");

		return mainProcess(bucketName, key, extension);
	}

	private void recievePage(Map<String, Object> input) throws IOException {
		String bucketName = (String) input.get("bucketName");
		String key = URLDecoder.decode((String) input.get("key"), "UTF-8");
		int pageNum = (int) input.get("pageNum");
		String pageId = (String) input.get("pageId");
		String extension = (String) input.get("extension");

		PDF pdf = new PDF(new S3(bucketName).readFile(key));
		File image = pdf.convertToImage(pageNum, extension);
		S3 s3Client = new S3(bucketName);
		s3Client.putObject(String.format("%s/%d-%s.%s", key, pageNum, pageId, extension), image);
		pdf.document.close();
	}
}
