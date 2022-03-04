from collections import defaultdict
import boto3
import time

import json
import logging
logger = logging.getLogger()
logger.setLevel(logging.INFO)


def lambda_handler(event, context):
    # TODO implement
    logger.info('got event{}'.format(event))
    print('DB instance restarted')
    
    # STARTING ASG INSTANCES
    asGroupNames = ['LinuxBastion01-062021-1.0']
    asclient = boto3.client('autoscaling')
    for asi in asGroupNames:
        print('starting ASG : ' + str(asi))
        response = asclient.set_desired_capacity(
            AutoScalingGroupName=str(asi),
            DesiredCapacity=1
        )
    asGroupNames = ['EC2ContainerService-opg-qa-cluster-EcsInstanceAsg-1RBTDYH4WA5QP']
    for asi in asGroupNames:
        print('starting ASG : ' + str(asi))
        response = asclient.set_desired_capacity(
            AutoScalingGroupName=str(asi),
            DesiredCapacity=2
        )
    print('Done all ASG are started.')
    
    # STARTING EC2 INSTANCES
    instanceIds = ['i-074b269dfc78db87e']
    ec2Client = boto3.client('ec2')
    ec2Client.start_instances(InstanceIds=instanceIds)
    
    # STARTING ECS INSTANCES
    # ecsClient = boto3.client('ecs')
    # clusterName = "opg-qa-cluster"
    # service_desired_count = 2
    # response = ecsClient.list_services(cluster=clusterName, maxResults=100)
    # serviceNames = ["main-qa", "data-listener-qa", "web-qa", "data-qa", "config-qa", "identity-qa", "metadata-qa", "email-qa", "sdk-qa", "nginx-qa"]

    # for serviceName in serviceNames:
    #     ecsClient.update_service(
    #         cluster=clusterName,
    #         service=serviceName,
    #         desiredCount=service_desired_count
    #     )
    #     print('Service started : ' + str(serviceName))

    
    #time.sleep(60)

    ec2 = boto3.resource('ec2')
    # Get information for all running instances
    running_instances = ec2.instances.filter(Filters=[{
        'Name': 'instance-state-name',
        'Values': ['running']}])

    ec2info = defaultdict()
    for instance in running_instances:
        for tag in instance.tags:
            if 'Name' in tag['Key']:
                name = tag['Value']
        # Add instance info to a dictionary
        ec2info[instance.id] = {
            'Name': name,
            'Type': instance.instance_type,
            'State': instance.state['Name'],
            'Private IP': instance.private_ip_address,
            'Public IP': instance.public_ip_address,
            'Launch Time': instance.launch_time
        }

    attributes = ['Name', 'Type', 'State',
                  'Private IP', 'Public IP', 'Launch Time']
    instanceArray = ''
    for instance_id, instance in ec2info.items():
        for key in attributes:
            instanceArray += ("{0}: {1}, ".format(key, instance[key]))
        instanceArray += ("\n")

    print(instanceArray)
    # Send message to SNS
    MY_SNS_TOPIC_ARN = 'arn:aws:sns:us-west-2:686227103548:opg-qa-ec2-up'
    sns_client = boto3.client('sns')
    message = instanceArray
    subject = 'OpptyGo-QA-EC2 Instances are UP'
    response = sns_client.publish(
        TopicArn=MY_SNS_TOPIC_ARN, Subject=subject, Message=message)
    print('response: ' + str(response))

    return {
        'statusCode': 200,
        'body': json.dumps('Successfully started ECS and EC2 instances')
    }
