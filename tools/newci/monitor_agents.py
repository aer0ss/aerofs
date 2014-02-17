#!/usr/bin/env python
"""
De-authorize disconnected agents and authorize connected agents.
If agent1 or agent2 are not connected, vagrant reload them.
"""
import os.path
import requests
import subprocess

AGENTS_TO_MONITOR = ["linux-virtual-1"]
AGENT_VAGRANT_DIR = os.path.join(os.path.abspath(__file__), 'agent-vagrant')
API_ADDR = 'https://192.168.128.197:8543/httpAuth/app/rest'
AUTH = ('aerofsbuild', 'temp123')
JSON_HEAD = {'accept': 'application/json'}

def get_all_agents():
    r = requests.get(API_ADDR + '/agents', auth=AUTH, headers=JSON_HEAD, verify=False)
    assert r.ok
    return r.json()["agent"]

def is_agent_connected(agentid):
    r = requests.get(API_ADDR + '/agents/id:{}/connected'.format(agentid), auth=AUTH, verify=False)
    assert r.ok
    return r.text == 'true'

def set_agent_authorization(agentid, val):
    data = str(val).lower()
    r = requests.put(API_ADDR + '/agents/id:{}/authorized'.format(agentid), data=data,  auth=AUTH, verify=False)

def reload_agent_by_name(name):
    cmd = "cd {} && vagrant halt --force {} && vagrant up".format(AGENT_VAGRANT_DIR, name)
    subprocess.check_call(cmd, shell=True)

def main():
    agents = get_all_agents()
    for agent in agents:
        agentid = int(agent["id"])
        if not is_agent_connected(agentid):
            set_agent_authorization(agentid, False)
    for agent in agents:
        agentid = int(agent["id"])
        if is_agent_connected(agentid):
            set_agent_authorization(agentid, True)
    for agent_name in AGENTS_TO_MONITOR:
        matching = [a for a in agents if a["name"] == agent_name]
        if len(matching) == 0 or not is_agent_connected(matching[0]["id"]):
            reload_agent_by_name(agent_name)

if __name__ == "__main__":
    main()
