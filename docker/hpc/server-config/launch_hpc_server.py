import sys
import boto3
from botocore.exceptions import NoCredentialsError
from os.path import dirname, realpath
import time


if len(sys.argv) != 2:
    print "Usage:\n    {cmd} <name of the server>\n\nExample:\n    {cmd} hpc-server-3".format(cmd=sys.argv[0])
    exit(1)

server_name = sys.argv[1]

ec2 = boto3.resource('ec2', region_name='us-east-1')

pwd = dirname(realpath(__file__))
with open(pwd + '/data/cloud-config') as f: user_data = f.read()

try:
    instance = ec2.create_instances(
        ImageId='ami-cbfdb2a1',  # CoreOS stable 835.9.0
        MinCount=1,
        MaxCount=1,
        KeyName='hpc-key',
        UserData=user_data,
        InstanceType='r3.2xlarge',
        BlockDeviceMappings=[
            {
                'DeviceName': '/dev/xvda',
                'Ebs': {
                    'SnapshotId': 'snap-7fcc68e9',
                    'VolumeSize': 300,
                    'DeleteOnTermination': True,
                    'VolumeType': 'gp2',
                },
            },
        ],
        NetworkInterfaces=[
            {
                'DeviceIndex': 0,
                'SubnetId': 'subnet-61f94b0c',  # public-production
                'Groups': ['sg-a76e09de'],  # aerofs.hosted_private_cloud
                'DeleteOnTermination': True,
                'AssociatePublicIpAddress': True
            },
        ],
    )
except NoCredentialsError:
    print """
Unable to locate your AWS credentials. You should export the environment
variables AWS_ACCESS_KEY_ID and AWS_SECRET_ACCESS_KEY with your AWS credentials.
For more info, see http://docs.aws.amazon.com/cli/latest/topic/config-vars.html
"""
    exit(1)

instance = instance[0]
instance.create_tags(Tags=[{'Key': 'Name', 'Value': server_name}])

while True:
    if instance.state.get('Name', None) != 'pending':
        break
    print "Waiting for instance to be ready. This should take no more than a minute."
    time.sleep(2)
    instance.reload()

if instance.state.get('Name', None) != 'running':
    print ""
    print "Something went wrong. The instance {} is in state: {}".format(instance.id, instance.state)
    print "Please check the AWS EC2 console and terminate the instance if needed:"
    print "https://console.aws.amazon.com/ec2/v2/home?region=us-east-1#Instances:search={};sort=Name \n".format(instance.id)
    exit(1)

print """
HPC server '{name}' created.

Instance ID: {instance_id}
Docker URL:  https://{private_ip}:2376
Public IP:   {public_ip}
SSH:         ssh -i secrets/hpc-key.pem core@{private_ip}

To configure this server, please run: ./configure_hpc_server.sh {private_ip}

""".format(
    name=server_name,
    instance_id=instance.id,
    private_ip=instance.private_ip_address,
    public_ip=instance.public_ip_address)

