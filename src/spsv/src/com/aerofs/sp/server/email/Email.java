package com.aerofs.sp.server.email;

public class Email implements IEmail{

        private final HTMLEmail _htmlEmail;
        private final TextEmail _textEmail;

        public Email(final String subject) {
                _htmlEmail = new HTMLEmail(subject);
                _textEmail = new TextEmail();
        }

        @Override
        public void addSection(String header, HEADER_SIZE size, String body) {
                _htmlEmail.addSection(header, size, body);
                _textEmail.addSection(header, size, body);

        }

        @Override
        public void addSignature(String valediction, String name, String ps) {
                _htmlEmail.addSignature(valediction, name, ps);
                _textEmail.addSignature(valediction, name, ps);

        }

        public String getTextEmail() {
                return _textEmail.getEmail();
        }

        public String getHTMLEmail() {
                return _htmlEmail.getEmail();
        }

}
