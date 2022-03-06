#!/usr/bin/env bash

function show_usage (){
    printf "Usage: $0 [options [parameters]]\n"
    printf "\n"
    printf "Options:\n"
    printf " -u|--customer, customer folder name inside customers folder[customer is required]\n"
    printf " -c|--cmd, valid commands ( synth,  deploy, destroy) [Optional, Default is deploy]\n"
    printf " -s|--stack, Valid roles (vpc, iam, global, aux, alb, ecs, ec2, rds) \n"
    printf " -h|--help, Print help \n"

return 0
}

# if less than three arguments supplied, display usage
	if [  $# -le 0 ]
	then
		show_usage
		exit 1
	fi

# check whether user had supplied -h or --help . If yes display usage
	if [[ ( $# == "--help") ||  $# == "-h" ]]
	then
		show_usage
		exit 0
	fi

while [ ! -z "$1" ]; do
  case "$1" in
     --customer|-u)
         shift
         CUSTOMER_ARG=$1
         ;;
     --cmd|-c)
         shift
         COMMAND_ARG=$1
         ;;
    --stack|-s)
        shift
        STACK_ARG=$1
         ;;
     *)
        show_usage
        exit 0
        ;;
  esac
shift
done

if [[ -z $ENV_ARG ]]
then
    ENV_ARG=dev2
fi

if [[ -z $COMMAND_ARG ]]
then
    COMMAND_ARG=deploy
fi

if [[ -z $CUSTOMER_ARG ]]
then
    show_usage
    exit 1
fi

source ./customers/$CUSTOMER_ARG/setup.sh

export CDK_VERSION="0.1"
export CONTEXT_PARAM="${CONTEXT_PARAM} -c keystorePath=${FEDERATE_JKS_PATH}"
export CONTEXT_PARAM="${CONTEXT_PARAM} -c keystorePwd=${FEDERATE_STORE_PWD}"
export CONTEXT_PARAM="${CONTEXT_PARAM} -c keystoreKeyPwd=${FEDERATE_KEY_PWD}"
export CONTEXT_PARAM="${CONTEXT_PARAM} -c version=${CDK_VERSION}"
export CONTEXT_PARAM="${CONTEXT_PARAM} -c logLevel=${CDK_LOG_LEVEL}"
export CONTEXT_PARAM="${CONTEXT_PARAM} -c path=${CDK_CONFIG_PATH}"
export CONTEXT_PARAM="${CONTEXT_PARAM} -c region=${CDK_REGION}"
export CONTEXT_PARAM="${CONTEXT_PARAM} -c prefix=${CDK_PFIX}"
export CONTEXT_PARAM="${CONTEXT_PARAM} -c org=${CDK_ORG}"
export CONTEXT_PARAM="${CONTEXT_PARAM} -c env=${CDK_ENV}"


export ARGS="$COMMAND_ARG -f ${CONTEXT_PARAM} -c stack=$STACK_ARG --require-approval=never"
echo "ARGS: $ARGS"
cdk $ARGS
