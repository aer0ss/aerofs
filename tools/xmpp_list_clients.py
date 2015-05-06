import logging

from sleekxmpp import ClientXMPP
from sleekxmpp.exceptions import IqError, IqTimeout

"""
XMPP Test client

See docs/tools/xmpp_test_client.md for installation and usage instructions.
"""

class EchoBot(ClientXMPP):

    def __init__(self, jid, password):
        ClientXMPP.__init__(self, jid, password)

        self.nick = jid

        self.add_event_handler("session_start", self.session_start)
        self.add_event_handler("message", self.message)

        # If you wanted more functionality, here's how to register plugins:
        self.register_plugin('xep_0030')  # Service Discovery
        self.register_plugin('xep_0045')  # MUC
        self.register_plugin('xep_0199')  # XMPP Ping
        self.register_plugin('xep_0054')  # vCard
        self.register_plugin('xep_0279')  # IP Check

        # Here's how to access plugins once you've registered them:
        # self['xep_0030'].add_feature('echo_demo')

        # If you are working with an OpenFire server, you will
        # need to use a different SSL version:
        # import ssl
        # self.ssl_version = ssl.PROTOCOL_SSLv3

    def session_start(self, event):
        self.send_presence()

        # vcard = self['xep_0054'].get_vcard('9f52b4be6d7a4bb3afe85d74581c4ffd-z@syncfs.com', cached=False)
        # print("Got the vCard data:", vcard['vcard_temp']['DESC'])

        self.get = 'all'
        self.target_jid = '04736070b993370e16d41e36771775de@c.syncfs.com'
        self.target_node = ''
        self.info_types = ['', 'all', 'info', 'identities', 'features']
        self.identity_types = ['', 'all', 'info', 'identities']
        self.feature_types = ['', 'all', 'info', 'features']
        self.items_types = ['', 'all', 'items']
        try:

            # Check my personal IP
            logging.info("The server told me my IP was: %s" % self['xep_0279'].check_ip(block=True))

            # Join the MUC
            logging.info("Joining MUC %s" % self.target_jid)
            self.plugin['xep_0045'].joinMUC(self.target_jid,
                                            self.nick,
                                            wait=True)

            if self.get in self.info_types:
                # By using block=True, the result stanza will be
                # returned. Execution will block until the reply is
                # received. Non-blocking options would be to listen
                # for the disco_info event, or passing a handler
                # function using the callback parameter.
                info = self['xep_0030'].get_info(jid=self.target_jid,
                                                 node=self.target_node,
                                                 block=True)
            if self.get in self.items_types:
                # The same applies from above. Listen for the
                # disco_items event or pass a callback function
                # if you need to process a non-blocking request.
                items = self['xep_0030'].get_items(jid=self.target_jid,
                                                   node=self.target_node,
                                                   block=True)

        except IqError as e:
            logging.error("Entity returned an error: %s" % e.iq['error']['condition'])
        except IqTimeout:
            logging.error("No response received.")
        else:
            header = 'XMPP Service Discovery: %s' % self.target_jid
            logging.info(header)
            logging.info('-' * len(header))
            if self.target_node != '':
                logging.info('Node: %s' % self.target_node)
                logging.info('-' * len(header))

            if self.get in self.identity_types:
                logging.info('Identities:')
                for identity in info['disco_info']['identities']:
                    logging.info('  - %s' % str(identity))

            if self.get in self.feature_types:
                logging.info('Features:')
                for feature in info['disco_info']['features']:
                    logging.info('  - %s' % feature)

            if self.get in self.items_types:
                logging.info('Items:')
                for item in items['disco_items']['items']:
                    logging.info('  - %s' % str(item))
                    vcard = self['xep_0054'].get_vcard(item[0], cached=False)
                    logging.info('    > %s' % vcard['vcard_temp']['DESC'])
                    logging.debug(vcard)

        finally:
            self.disconnect()

    def message(self, msg):
        if msg['type'] in ('chat', 'normal'):
            msg.reply("Thanks for sending\n%(body)s" % msg).send()


if __name__ == '__main__':
    # Ideally use optparse or argparse to get JID,
    # password, and log level.

    logging.basicConfig(level=logging.INFO,
                        format='%(levelname)-8s %(message)s')

    xmpp = EchoBot('johnsnow@syncfs.com', 'kikoo')
    xmpp.connect(address=('share.syncfs.com', 5222))
    xmpp.process(block=True)
