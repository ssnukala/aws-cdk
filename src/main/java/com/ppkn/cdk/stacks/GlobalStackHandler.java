package com.ppkn.cdk.stacks;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.ppkn.cdk.config.GlobalConfigModel;
import com.ppkn.cdk.config.Parameters;
import com.ppkn.cdk.config.SsmConfigModel;
import com.ppkn.cdk.config.StackEnum;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.Nullable;
import software.amazon.awscdk.core.*;
import software.amazon.awscdk.services.ecr.Repository;
import software.amazon.awscdk.services.ecr.RepositoryProps;
import software.amazon.awscdk.services.ecr.TagMutability;
import software.amazon.awscdk.services.s3.BlockPublicAccess;
import software.amazon.awscdk.services.s3.Bucket;
import software.amazon.awscdk.services.s3.BucketEncryption;
import software.amazon.awscdk.services.s3.BucketProps;
import software.amazon.awscdk.services.sns.Topic;
import software.amazon.awscdk.services.sns.TopicProps;
import software.amazon.awscdk.services.sns.subscriptions.EmailSubscription;
import software.amazon.awscdk.services.sqs.Queue;
import software.amazon.awscdk.services.sqs.QueueProps;

import java.io.File;

@Slf4j
public class GlobalStackHandler implements AwsStackHandler {

    private final Parameters parameters;
    private GlobalConfigModel globalConfigModel;

    public GlobalStackHandler(final Parameters parameters) {
        this.parameters = parameters;
    }

    @Override
    public void processStack() {
        readConfigModel();
        String accountId = globalConfigModel.getAccountId();
        log.info("processStack::accountId : {}", accountId);
        new GlobalStack(parameters.getApp(), globalConfigModel.getStackName(), new StackProps() {
            @Override
            public @Nullable Environment getEnv() {
                return new Environment() {
                    @Override
                    public String getAccount() {
                        return accountId;
                    }

                    @Override
                    public String getRegion() {
                        return globalConfigModel.getRegion();
                    }
                };
            }
        });

    }

    private void readConfigModel() {
        try {
            String configFilePath = parameters.getConfigPath(StackEnum.global);
            log.info("Reading Global stack config model : {} ", configFilePath);
            ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
            mapper.findAndRegisterModules();
            globalConfigModel = mapper.readValue(new File(configFilePath), GlobalConfigModel.class);
            globalConfigModel.processPlaceHolders(parameters);

            log.debug("Done Reading config model: {}", globalConfigModel);
            log.info("Successfully loaded config model");
        } catch (Exception e) {
            log.error("Error in populateStackMap : ", e);
            throw new RuntimeException(e);
        }
    }

    public class GlobalStack extends AbstractStack {

        public GlobalStack(final Construct scope, final String id, final StackProps props) {
            super(scope, id, props);
            createS3Buckets();
            if( globalConfigModel.getSns() != null && !globalConfigModel.getSns().isEmpty()) {
                createSnsTopics();
            }
            if( globalConfigModel.getSqs() != null && !globalConfigModel.getSqs().isEmpty()) {
                createSqaQueues();
            }
            if( globalConfigModel.getEcr() != null && !globalConfigModel.getEcr().isEmpty()) {
                createEcr();
            }
        }

        private void createEcr() {
            globalConfigModel.getEcr().forEach(ecrModel -> {
                Repository repo = new Repository(this, ecrModel.getName(), RepositoryProps.builder()
                        .repositoryName(ecrModel.getName())
                        .imageScanOnPush(true)
                        .imageTagMutability(TagMutability.IMMUTABLE)
                        .build());
                super.processTags(repo, ecrModel);
            });
        }
        private void createSqaQueues() {
            globalConfigModel.getSqs().forEach(sqsModel -> {
                Queue queue = new Queue(this, sqsModel.getName(), QueueProps
                    .builder()
                    //.fifo(sqsModel.isFifo())
                    .queueName(sqsModel.getName())
                    .retentionPeriod(Duration.days(sqsModel.getRetentionPeriod()))
                    .build());
                super.processTags(queue, sqsModel);
                super.putSsmStringParameter(SsmConfigModel.builder()
                    .ssmKey(sqsModel.getSsmKey())
                    .value(queue.getQueueArn())
                    .build());
            });
        }

        private void createSnsTopics() {
            globalConfigModel.getSns().forEach(snsModel -> {
                Topic topic = new Topic(this, snsModel.getName(),
                    TopicProps.builder()
                        .topicName(snsModel.getName())
                        .build());
                snsModel.getEmailAddress().forEach(email -> topic.addSubscription(new EmailSubscription(email)));
                super.processTags(topic, snsModel);
                super.putSsmStringParameter(SsmConfigModel.builder()
                                                        .ssmKey(snsModel.getSsmKey())
                                                        .value(topic.getTopicArn())
                                                    .build());
            });
        }

        private void createS3Buckets() {

            globalConfigModel.getS3Buckets().forEach(s3Model -> {
                Bucket bucket = new Bucket(this, s3Model.getName(),
                    BucketProps.builder()
                        .bucketName(s3Model.getName())
                        .versioned(false)
                        .encryption(BucketEncryption.S3_MANAGED)
                        .publicReadAccess(false)
                        .blockPublicAccess(BlockPublicAccess.BLOCK_ALL)
                        .removalPolicy(RemovalPolicy.DESTROY)
                        .build());
                super.processTags(bucket, s3Model);
                super.putSsmStringParameter(SsmConfigModel
                    .builder()
                    .ssmKey(s3Model.getSsmKey())
                    .value(bucket.getBucketArn())
                    .build());
            });
        }

    }

}
