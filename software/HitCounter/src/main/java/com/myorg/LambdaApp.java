package com.myorg;

import java.util.HashMap;
import java.util.Map;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDBAsync;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBAsyncClientBuilder;
import com.amazonaws.services.dynamodbv2.document.DynamoDB;
import com.amazonaws.services.dynamodbv2.document.Item;
import com.amazonaws.services.dynamodbv2.document.Table;
import com.amazonaws.services.dynamodbv2.document.UpdateItemOutcome;
import com.amazonaws.services.dynamodbv2.document.spec.GetItemSpec;
import com.amazonaws.services.dynamodbv2.document.spec.UpdateItemSpec;
import com.amazonaws.services.dynamodbv2.document.utils.ValueMap;
import com.amazonaws.services.dynamodbv2.model.ReturnValue;
import com.amazonaws.services.dynamodbv2.model.UpdateItemResult;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;

public class LambdaApp implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {
    private Table table;
    private String primaryKey = System.getenv("PRIMARY_KEY");
    private String functionName = System.getenv("FUNCTION_NAME");
    private String tableName = System.getenv("TABLE_NAME");
    private LambdaLogger logger;

    public APIGatewayProxyResponseEvent handleRequest(final APIGatewayProxyRequestEvent input, final Context context) {
        this.logger = context.getLogger();
        context.getLogger().log("Input: " + input);
        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", "application/json");
        headers.put("X-Custom-Header", "application/json");

        this.initDynamoDb();
        this.incrementCounter();
        APIGatewayProxyResponseEvent response = new APIGatewayProxyResponseEvent().withHeaders(headers);
        Item latestEntry = getLatestCounter();
        
        return response.withStatusCode(200).withBody(latestEntry.toJSON());
    }

    private void initDynamoDb() {
        String region = System.getenv("REGION");

        AmazonDynamoDBAsync client = AmazonDynamoDBAsyncClientBuilder.standard().withRegion(region).build();

        DynamoDB dynamoDb = new DynamoDB(client);
        this.table = dynamoDb.getTable(tableName);
    }

    private void incrementCounter() {
        logger.log("Incrementing counter");
        UpdateItemResult result = new UpdateItemResult();
        UpdateItemSpec updateItemSpec = new UpdateItemSpec()
                .withPrimaryKey(this.primaryKey, this.functionName)
                .withUpdateExpression("set count = count  + :val")
                // .withConditionExpression("size(info.actors) > :num")
                .withValueMap(new ValueMap().withNumber(":val", 1)).withReturnValues(ReturnValue.UPDATED_NEW);
        
        try {
            UpdateItemOutcome outcome = this.table.updateItem(updateItemSpec);
            result = outcome.getUpdateItemResult();
        } catch (Exception e) {
            logger.log("Unable to update item:");
            logger.log(e.getMessage());
            
        }
        logger.log(result.toString());
    }

    private Item getLatestCounter(){
        GetItemSpec itemSepec = new GetItemSpec().withPrimaryKey(this.primaryKey, this.functionName);
        Item initItem = new Item();

        try {
            Item item = this.table.getItem(itemSepec);
            if (item.isPresent(this.functionName)) {
                return item;
            }
        } catch (Exception e) {
            logger.log("Unable to get latest item:");
            logger.log(e.getMessage());
        }
        return initItem;
    }
}