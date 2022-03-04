from collections import defaultdict
import boto3
import time
import json
import logging
logger = logging.getLogger()
logger.setLevel(logging.INFO)


def lambda_handler(event, context):
    logger.info('Got Start event{} from APIGateway'.format(event))

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
    # MY_SNS_TOPIC_ARN = 'arn:aws:sns:us-west-2:686227103548:opg-qa-ec2-up'
    # sns_client = boto3.client('sns')
    # message = instanceArray
    # subject = 'OpptyGo-QA-EC2 Instances are UP'
    # response = sns_client.publish(
    #     TopicArn=MY_SNS_TOPIC_ARN, Subject=subject, Message=message)
    # print('response: ' + str(response))

    # elb = boto3.client('elbv2')
    # response = elb.register_targets(
    # TargetGroupArn='arn:aws:elasticloadbalancing:us-west-2:686227103548:targetgroup/ecs-opg-web-qa/8cf9ce551e9db46b',
    # Targets=[
    #         {
    #             'Id': 'i-0316efdc2eb6da4bb',
    #             'Port': 9000
    #         },
    #         {
    #             'Id': 'i-0eca2100560f48e43',
    #             'Port': 9000
    #         },
    #     ],
    # )
    # print('alb web TG ' + str(response))
    
    return {
        'statusCode': 200,
        'body': json.dumps('Successfully received your start request')
    }
