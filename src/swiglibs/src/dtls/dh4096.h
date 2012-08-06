/*
 * dh4096.h
 *
 *  Created on: Jul 18, 2011
 *      Author: yuris
 */

#ifndef DH4096_H_
#define DH4096_H_

#include <openssl/dh.h>

DH *get_dh4096()
	{
	static unsigned char dh4096_p[]={
		0xD0,0x12,0x89,0x1E,0x66,0xB5,0x0F,0x98,0x7C,0x14,0x30,0x85,
		0x7C,0x9E,0x34,0xB9,0xB2,0xD2,0x5A,0x58,0x25,0xAF,0xC8,0x6C,
		0x5D,0xAA,0xB3,0xA2,0x35,0x7C,0x0D,0x1F,0x7A,0x33,0xCD,0x72,
		0xBC,0xCA,0xED,0x7D,0x1B,0xB3,0x6E,0xD3,0xDB,0x0C,0x0C,0x2D,
		0x9D,0x6D,0x7F,0xAC,0xEC,0xC6,0x2E,0xDC,0xE1,0xF1,0x50,0xDB,
		0xAD,0xB2,0x80,0x02,0x38,0x6B,0x2C,0xE6,0x6E,0xD3,0xE4,0x01,
		0xB9,0x6A,0xE7,0x6E,0xD5,0xC8,0xF7,0x3F,0x93,0x52,0x19,0xFD,
		0xE5,0xD9,0x91,0x35,0x2F,0x10,0x6D,0xB1,0x33,0x81,0xAF,0x4C,
		0xF4,0xEA,0xA1,0x55,0x04,0xEC,0x68,0x22,0xFE,0x20,0x8A,0xCC,
		0xCA,0xCF,0x03,0xAA,0xA5,0xA4,0x08,0x48,0xA3,0x19,0xA5,0xCC,
		0xD4,0xDE,0x57,0x43,0xCF,0xF9,0x02,0xD4,0xB4,0xAF,0x6A,0xEB,
		0x16,0x6F,0x8A,0xD1,0xF1,0x82,0x19,0x1C,0xC1,0x67,0x10,0xAB,
		0xF5,0xB0,0x73,0x63,0xD2,0xDE,0xA2,0x4A,0xD2,0x8D,0x6F,0x35,
		0xEA,0xD7,0x79,0x51,0x6F,0x1F,0x91,0x70,0xAF,0xE7,0x56,0xF1,
		0x1A,0x50,0x6F,0x6C,0x4E,0x5C,0x7D,0x28,0x33,0x18,0x45,0x09,
		0x39,0x83,0x94,0x36,0x1F,0xE1,0x14,0x51,0x98,0x12,0x5B,0x03,
		0xF3,0x58,0xD2,0xA1,0x61,0x05,0xD7,0x76,0xFE,0x60,0x76,0x8A,
		0x07,0xB1,0xE5,0x6E,0x05,0x2F,0x7F,0x48,0x67,0x6E,0x08,0x83,
		0xF4,0xC0,0xD2,0xD4,0x69,0x81,0xFE,0xF2,0x07,0x06,0x70,0x67,
		0x0F,0xA3,0xAB,0xDF,0x5A,0x01,0x86,0x79,0x4F,0xCD,0x37,0x1B,
		0xAD,0x24,0xF8,0x55,0xB6,0xE6,0x05,0xCE,0x5D,0x83,0xC2,0xC4,
		0xD5,0xC0,0x02,0xA6,0x15,0x89,0x15,0xBE,0x12,0xA0,0x47,0x34,
		0x4E,0x04,0x54,0xF5,0xFB,0xB2,0x48,0xC2,0x46,0x60,0xFF,0xD8,
		0x29,0xB8,0x69,0x01,0x8C,0xA4,0xB8,0xEF,0xC1,0x1A,0xCF,0x32,
		0x64,0x69,0xCE,0x02,0x03,0xB0,0x19,0x1B,0x4A,0xAB,0xFB,0x77,
		0xE6,0xBA,0x31,0x03,0x02,0x56,0x30,0xB3,0x17,0x51,0x69,0x7C,
		0x47,0xFB,0x14,0x49,0x84,0x20,0xAE,0x1E,0x88,0xBC,0x27,0x03,
		0x79,0x6F,0x03,0x81,0xEF,0xBF,0xF9,0xAE,0x10,0x09,0xCA,0xE8,
		0xF2,0x44,0x50,0x22,0x57,0xBF,0xE2,0x04,0x3E,0x96,0xA7,0x55,
		0x7C,0xC4,0x51,0xAC,0x50,0xA9,0x31,0x3A,0xB9,0x92,0xC1,0xDD,
		0xC9,0x1B,0xD2,0xA8,0xDF,0xE2,0x14,0xC7,0xF9,0xAE,0x4F,0xB8,
		0xE4,0x5A,0xB5,0xA0,0xA4,0xCF,0xFC,0x08,0xC6,0x3E,0x1A,0x7E,
		0x49,0x3E,0x50,0x7B,0x44,0xD8,0x96,0xDF,0xD4,0x0B,0x67,0x03,
		0x58,0x7E,0x50,0xC4,0x12,0xB5,0x92,0xD3,0xAF,0xC4,0x44,0xC7,
		0xD0,0x5D,0x68,0x6B,0x0A,0x23,0xC7,0xE1,0xBA,0xE5,0x15,0x8F,
		0x8E,0xFC,0xB9,0x11,0xE3,0x13,0x01,0x64,0x2B,0x7C,0xFE,0x56,
		0xD3,0x38,0xF0,0x20,0x08,0xB3,0xE9,0x84,0xAD,0xB2,0xDD,0xD6,
		0xA5,0x40,0x2C,0xE2,0xCB,0x55,0xD7,0x8E,0x74,0x80,0x79,0x8B,
		0x5A,0xEF,0x76,0x0B,0xEE,0x04,0x40,0xCE,0x7C,0x78,0xF8,0x8D,
		0x55,0xFA,0xE4,0x55,0x6D,0x5D,0xFE,0x54,0xFD,0x41,0x33,0xAD,
		0xFE,0x32,0xD6,0xF5,0x7C,0x3A,0xCD,0x0A,0x85,0xB7,0x8C,0xBF,
		0xE9,0xF7,0xC9,0x08,0xAB,0xB8,0x3C,0x85,0xD8,0x63,0x7F,0x08,
		0xB1,0xB7,0x5B,0x9D,0xA8,0xC6,0x97,0xCB,
		};
	static unsigned char dh4096_g[]={
		0x02,
		};
	DH *dh;

	if ((dh=DH_new()) == NULL) return(NULL);
	dh->p=BN_bin2bn(dh4096_p,sizeof(dh4096_p),NULL);
	dh->g=BN_bin2bn(dh4096_g,sizeof(dh4096_g),NULL);
	if ((dh->p == NULL) || (dh->g == NULL))
		{ DH_free(dh); return(NULL); }
	return(dh);
	}


#endif /* DH4096_H_ */
