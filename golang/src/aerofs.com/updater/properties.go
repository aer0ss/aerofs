package main

import (
	"bytes"
	"fmt"
	"io/ioutil"
	"strconv"
	"unicode/utf8"
)

var errInvalidProperties error = fmt.Errorf("invalid properties")

func isSpace(c byte) bool {
	return c == ' ' || c == '\t' || c == '\f'
}

func isKeyValueSeparator(c byte) bool {
	return c == '=' || c == ':' || isSpace(c)
}

func unescape(d []byte, atEnd func(byte) bool, multiline bool) (string, []byte) {
	idx := 0
	last := 0
	r := ""
	for idx < len(d) {
		if d[idx] == '\\' {
			if idx > last {
				r += string(d[last:idx])
			}
			idx++
			switch d[idx] {
			case '\r':
				if idx+1 < len(d) && d[idx+1] == '\n' {
					idx++
				}
				fallthrough
			case '\n':
				if multiline {
					idx++
					for idx < len(d) && isSpace(d[idx]) {
						idx++
					}
					last = idx
					continue
				} else {
					return "", nil
				}
			case 'n':
				r += "\n"
			case 't':
				r += "\t"
			case 'r':
				r += "\r"
			case 'u':
				if idx+5 > len(d) {
					return "", nil
				}
				codepoint, err := strconv.ParseInt(string(d[idx+1:idx+5]), 16, 16)
				if err != nil || !utf8.ValidRune(rune(codepoint)) {
					return "", nil
				}
				buf := make([]byte, 4)
				n := utf8.EncodeRune(buf, rune(codepoint))
				r += string(buf[:n])
				idx += 4
			default:
				r += string(d[idx : idx+1])
			}
			idx++
			last = idx
			continue
		}
		if atEnd(d[idx]) {
			break
		}
		idx++
	}
	if last < idx {
		r += string(d[last:idx])
	}
	return r, d[idx:]
}

func LoadJavaProperties(file string) (map[string]string, error) {
	d, err := ioutil.ReadFile(file)
	if err != nil {
		return nil, fmt.Errorf("Could not read file %s:\n%s", file, err.Error())
	}
	var key, value string
	m := make(map[string]string)
	for len(d) > 0 {
		idx := 0
		// ignore leading whitespace and empty lines
		for idx < len(d) && (isSpace(d[idx]) || d[idx] == '\r' || d[idx] == '\n') {
			idx++
		}
		// ignore comment lines
		if d[idx] == '#' || d[idx] == '!' {
			idx = bytes.IndexByte(d, '\n')
			if idx == -1 {
				break
			}
			d = d[idx+1:]
			continue
		}
		key, d = unescape(d[idx:], isKeyValueSeparator, false)
		if d == nil {
			return nil, errInvalidProperties
		}
		idx = 0
		for idx < len(d) && isSpace(d[idx]) {
			idx++
		}
		if idx < len(d) && (d[idx] == '=' || d[idx] == ':') {
			idx++
		}
		for idx < len(d) && isSpace(d[idx]) {
			idx++
		}
		value, d = unescape(d[idx:], func(c byte) bool {
			return c == '\n' || c == '\r'
		}, true)
		if d == nil {
			return nil, errInvalidProperties
		}
		m[key] = value
	}
	return m, nil
}
