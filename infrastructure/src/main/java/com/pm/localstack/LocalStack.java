package com.pm.localstack;

import com.amazonaws.services.dynamodbv2.xspec.S;
import com.amazonaws.services.rds.model.DBInstance;
import software.amazon.awscdk.*;
import software.amazon.awscdk.services.ec2.*;
import software.amazon.awscdk.services.ec2.InstanceType;
import software.amazon.awscdk.services.ecs.*;
import software.amazon.awscdk.services.ecs.Protocol;
import software.amazon.awscdk.services.logs.LogGroup;
import software.amazon.awscdk.services.logs.RetentionDays;
import software.amazon.awscdk.services.msk.CfnCluster;
import software.amazon.awscdk.services.rds.*;
import software.amazon.awscdk.services.route53.CfnHealthCheck;
import software.amazon.awscdk.services.route53.Continent;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class LocalStack extends Stack {

    private final Vpc vpc;
    private final Cluster cluster;


    public LocalStack(final App scope, final String id, final StackProps props) {

        super(scope, id, props);
        this.vpc = createVpc();

        DatabaseInstance authServiceDB =
                createDatabase("AuthServiceDB", "auth-service-db");

        DatabaseInstance patientServiceDB =
                createDatabase("PatientServiceDB", "patient-service-db");

        CfnHealthCheck authServiceDBHealthCheck = createHealthCheck(authServiceDB, "auth-service-db-health-check");
        CfnHealthCheck patientServiceDBHealth = createHealthCheck(authServiceDB, "patient-service-db-health-check");
        CfnCluster mskCluster = createMskCluster();

        this.cluster = createEcsCluster();

        FargateService authService =
                createFargateService("AuthService",
                        "auth-service",
                        List.of(4005),
                        authServiceDB,
                        Map.of("JWT_SECRET","e4d87573c83454842b4d4b45d106cb2d6f7079d7c16d324c8d7eebacf23296ee"));


        authService.getNode().addDependency(authServiceDBHealthCheck);
        authService.getNode().addDependency(authServiceDB);

        FargateService billingService =
                createFargateService("BillingService",
                        "billing-service",
                        List.of(4001,9001),
                        null,
                        null);


        FargateService analyticsService =
                createFargateService("AnalyticsService",
                        "analytics-service",
                        List.of(4002),
                        null,
                        null);

        analyticsService.getNode().addDependency(mskCluster);


        FargateService patientService =
                createFargateService("PatientService",
                        "patient-service",
                        List.of(4000),
                        patientServiceDB,
                        Map.of(
                                "BILLING_SERVICE_ADDRESS","host.docker.internal",
                                "BILLING_SERVICE_GRPC_PORT","9001"
                        ));
        patientService.getNode().addDependency(patientServiceDB);
        patientService.getNode().addDependency(patientServiceDBHealth);
        patientService.getNode().addDependency(billingService);
        patientService.getNode().addDependency(mskCluster);
    }


    public static void main(String[] args) {
        App app = new App(AppProps.builder().outdir("./cdk.out").build());

        StackProps props = StackProps.builder()
                .synthesizer(new BootstraplessSynthesizer())
                .build();

        new LocalStack(app, "localstack", props);
        app.synth();
        System.out.println("Synthesizing Local stack");
    }

    //creates a VPC where all the services reside
    private Vpc createVpc() {
        return Vpc.Builder.create(this, "PatientManagementVPC")
                .vpcName("PatientManagementVPC")
                .maxAzs(2)
                .build();
    }

    //creates RDS service inside the VPC
    private DatabaseInstance createDatabase(String id, String dbName) {
        return DatabaseInstance.Builder
                .create(this, id)
                .engine(DatabaseInstanceEngine.postgres(
                        PostgresInstanceEngineProps.builder()
                                .version(PostgresEngineVersion.VER_17_2)
                                .build()))
                .vpc(vpc)
                .instanceType(InstanceType.of(InstanceClass.BURSTABLE2, InstanceSize.MICRO))
                .allocatedStorage(20)
                .credentials(Credentials.fromGeneratedSecret("admin_user"))
                .databaseName(dbName)
                .removalPolicy(RemovalPolicy.DESTROY)
                .build();
    }

    //healthCheck for cheking the health of db
    private CfnHealthCheck createHealthCheck(DatabaseInstance db, String id) {
        return CfnHealthCheck.Builder.create(this, id)
                .healthCheckConfig(CfnHealthCheck.HealthCheckConfigProperty.builder()
                        .type("TCP")
                        .port(Token.asNumber(db.getDbInstanceEndpointPort()))
                        .ipAddress(db.getDbInstanceEndpointAddress())
                        .requestInterval(30)
                        .failureThreshold(3)
                        .build())
                .build();

    }

    //Creates Msk (Kafka) inside the VPC
    private CfnCluster createMskCluster() {
        return CfnCluster.Builder.create(this, "MskCluster")
                .clusterName("Kafka-Cluster")
                .kafkaVersion("2.8.0")
                .numberOfBrokerNodes(1)
                .brokerNodeGroupInfo(CfnCluster.BrokerNodeGroupInfoProperty.builder()
                        .instanceType("kafka.m5.xlarge")
                        .clientSubnets(vpc.getPrivateSubnets().stream()
                                .map(ISubnet::getSubnetId)
                                .collect(Collectors.toList()))
                        .brokerAzDistribution("DEFAULT")
                        .build())
                .build();

    }

    //create ECS Cluster inside the VPC
    private Cluster createEcsCluster() {
        return Cluster.Builder.create(this, "PatientManagementCluster")
                .vpc(vpc)
                .defaultCloudMapNamespace(CloudMapNamespaceOptions.builder()
                        .name("patient-management.local").build())
                .build();
    }

    //Creates a EC2 service
    private FargateService createFargateService(
            String id,
            String imageName,
            List<Integer> ports,
            DatabaseInstance db,
            Map<String, String> envVars) {

        FargateTaskDefinition taskDefinition =
                FargateTaskDefinition.Builder.create(this, id+"Task")
                        .cpu(256)
                        .memoryLimitMiB(512)
                        .build();

        //building image
        ContainerDefinitionOptions.Builder containerDefinitionOptions =
                ContainerDefinitionOptions.builder()
                        .image(ContainerImage.fromRegistry(imageName))
                        .portMappings(ports.stream()
                                .map(port -> PortMapping.builder()
                                        .containerPort(port)
                                        .hostPort(port)
                                        .protocol(Protocol.TCP)
                                        .build())
                                .toList())
                        .logging(LogDriver.awsLogs(AwsLogDriverProps.builder()
                                .logGroup(LogGroup.Builder.create(this, id + "LogGroup")
                                        .logGroupName("/ecs/" + imageName)
                                        .removalPolicy(RemovalPolicy.DESTROY)
                                        .retention(RetentionDays.ONE_DAY)
                                        .build())
                                        .streamPrefix(imageName)
                                .build()));




        Map<String, String> eVars = new HashMap<>();

        eVars.put("SPRING_KAFKA_BOOTSTRAP_SERVERS","localhost.localstack.cloud:4510 ,localhost.localstack.cloud:4511,localhost.localstack.cloud:4512");

        if(envVars!=null){
            eVars.putAll(envVars);
        }

        if(db!=null){
            eVars.put("SPRING_DATASOURCE_URL","jdbc:postgresql://%s:%s/%s-db".formatted(
                    db.getDbInstanceEndpointAddress(),
                    db.getDbInstanceEndpointPort(),
                    imageName
            ));

            eVars.put("SPRING_DATASOURCE_USERNAME","admin_user");
            eVars.put("SPRING_DATASOURCE_PASSWORD",db.getSecret().secretValueFromJson("password").toString());
            eVars.put("SPRING_JPA_HIBERNATE_DDL_AUTO","update");
            eVars.put("SPRING_SQL_INTI_MODE","always");
            eVars.put("SPRING_DATASOURCE_HIKARI_INITIALIZATION_FAIL_TIMEOUT","60000");

            containerDefinitionOptions.environment(eVars);
            containerDefinitionOptions.essential(true);
            taskDefinition.addContainer(imageName + "Container",containerDefinitionOptions.build());
        }

        return FargateService.Builder.create(this, id)
                .cluster(cluster)
                .taskDefinition(taskDefinition)
                .assignPublicIp(false)
                .serviceName(imageName)
                .build();



    }

}
