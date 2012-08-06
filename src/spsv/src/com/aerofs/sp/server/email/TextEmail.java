package com.aerofs.sp.server.email;

public class TextEmail implements IEmail {

        private final StringBuilder _sb = new StringBuilder();

        @Override
        public void addSection(final String header, final HEADER_SIZE size, final String body) {

                int len = header.length();

                _sb.append("\n");

                String underline;
                if (size == HEADER_SIZE.H1) {
                        underline = "=";
                } else {
                        underline = "-";
                }

                _sb.append(header);

                _sb.append("\n");
                for (int i =0; i < len; i++) _sb.append(underline);
                _sb.append("\n");

                _sb.append("\n");
                _sb.append(body);

        }

        @Override
        public void addSignature(String valediction, String name, String ps) {
                _sb.append("\n\n");
                _sb.append(valediction);
                _sb.append("\n" + name);

                _sb.append("\n\n" + ps);

        }

        public String getEmail() {
                return _sb.toString();
        }

}
