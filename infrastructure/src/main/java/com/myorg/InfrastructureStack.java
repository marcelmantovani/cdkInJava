package com.myorg;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import software.amazon.awscdk.core.CfnOutput;
import software.amazon.awscdk.core.CfnOutputProps;
import software.amazon.awscdk.core.Construct;
import software.amazon.awscdk.core.Duration;
import software.amazon.awscdk.core.Stack;
import software.amazon.awscdk.core.StackProps;
import software.amazon.awscdk.services.apigatewayv2.AddRoutesOptions;
import software.amazon.awscdk.services.apigatewayv2.HttpApi;
import software.amazon.awscdk.services.apigatewayv2.HttpApiProps;
import software.amazon.awscdk.services.apigatewayv2.HttpMethod;
import software.amazon.awscdk.services.apigatewayv2.PayloadFormatVersion;
import software.amazon.awscdk.services.apigatewayv2.integrations.LambdaProxyIntegration;
import software.amazon.awscdk.services.apigatewayv2.integrations.LambdaProxyIntegrationProps;
import software.amazon.awscdk.services.dynamodb.Attribute;
import software.amazon.awscdk.services.dynamodb.AttributeType;
import software.amazon.awscdk.services.dynamodb.Table;
import software.amazon.awscdk.services.dynamodb.TableProps;
import software.amazon.awscdk.services.lambda.Code;
import software.amazon.awscdk.services.lambda.Function;
import software.amazon.awscdk.services.lambda.FunctionProps;
import software.amazon.awscdk.services.lambda.Runtime;
import software.amazon.awscdk.services.logs.RetentionDays;

public class InfrastructureStack extends Stack {

    public InfrastructureStack(final Construct parent, final String id) {
        this(parent, id, null);
    }
    public InfrastructureStack(final Construct parent, final String id, final StackProps props) {
        super(parent, id, props);

        String tableName = "hit_counter";
        String handlerName = "com.myorg.LambdaApp";
        String primaryKeyName = "name";

        // Create Dynamo DB table
        TableProps tableProps;
        Attribute partitionKey = Attribute.builder()
            .name("primaryKeyName")
            .type(AttributeType.STRING)
            .build();
        
        tableProps = TableProps.builder()
            .partitionKey(partitionKey)
            .tableName(tableName)
            .build();
        
        Table table = new Table(this, tableName, tableProps);

        // Deploy Lambda function from assets .jar file
        Map<String, String> env = new HashMap<>();
        env.put("TABLE_NAME", tableName);
        env.put("PRIMARY_KEY", primaryKeyName);
        env.put("FUNCTION_NAME", handlerName);

        Function hitCountFunction = new Function(this, "HitCountFn", FunctionProps.builder()
            .runtime(Runtime.JAVA_11)
            .code(Code.fromAsset("../assets/"))
            .handler(handlerName)
            .memorySize(1024)
            .timeout(Duration.seconds(10))
            .logRetention(RetentionDays.THREE_DAYS)
            .environment(env)
            .build());
        
        

        // Grant lambda permission to read and write into DDB table
        table.grantReadWriteData(hitCountFunction);

        // new api gateway and associate method to lambda handler
        HttpApi httpApi = new HttpApi(this, "sample-api", HttpApiProps.builder()
                .apiName("sample-api")
                .build());
            
        httpApi.addRoutes(AddRoutesOptions.builder()
            .path("/count")
            // .methods(Collections.singletonList(HttpMethod.GET))
            .methods(new ArrayList<>(Arrays.asList(HttpMethod.GET)))
            .integration(new LambdaProxyIntegration(LambdaProxyIntegrationProps.builder()
                        .handler(hitCountFunction)
                        .payloadFormatVersion(PayloadFormatVersion.VERSION_2_0)
                        .build()))
            .build()
            );
        
        // output api gateway url
        new CfnOutput(this, "HttpApi", CfnOutputProps.builder()
                        .description("Use the browser to access the API url")
                        .value(httpApi.getApiEndpoint())
                        .build());
    }
}
