package com.googlecode.pngtastic.core;

import org.junit.jupiter.api.Test;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 *
 */
class PngReaderTest {

	@Test
	void getRGBA8() throws Exception {
		final List<String> files = Arrays.asList(
//				"basi0g01.png", "basi0g02.png", "basi0g04.png",
				"basi0g08.png", "basi0g16.png", "basi2c08.png", "basi2c16.png",
//				"basi3p01.png", "basi3p02.png", "basi3p04.png",
				"basi3p08.png", "basi4a08.png", "basi4a16.png", "basi6a08.png", "basi6a16.png",
				"basn0g01.png", "basn0g02.png",
//				"basn0g04.png",
				"basn0g08.png", "basn0g16.png",
				"basn2c08.png", "basn2c16.png", "basn3p01.png", "basn3p02.png",
//				"basn3p04.png",
				"basn3p08.png", "basn4a08.png", "basn4a16.png", "basn6a08.png", "basn6a16.png",
				"bgai4a08.png", "bgai4a16.png", "bgan6a08.png", "bgan6a16.png", "bgbn4a08.png", "bggn4a16.png",
				"bgwn6a08.png", "bgyn6a16.png", "ccwn2c08.png", "ccwn3p08.png", "cdfn2c08.png", "cdhn2c08.png",
				"cdsn2c08.png", "cdun2c08.png",
//				"ch1n3p04.png",
				"ch2n3p08.png",
//				"cm0n0g04.png", "cm7n0g04.png", "cm9n0g04.png",
				"cs3n2c16.png", "cs3n3p08.png", "cs5n2c08.png", "cs5n3p08.png", "cs8n2c08.png", "cs8n3p08.png",
//				"ct0n0g04.png", "ct1n0g04.png", "ctzn0g04.png",
				"f00n0g08.png", "f00n2c08.png", "f01n0g08.png", "f01n2c08.png", "f02n0g08.png", "f02n2c08.png",
				"f03n0g08.png", "f03n2c08.png", "f04n0g08.png", "f04n2c08.png",
				"g03n0g16.png", "g03n2c08.png",
//				"g03n3p04.png",
				"g04n0g16.png", "g04n2c08.png",
//				"g04n3p04.png",
				"g05n0g16.png", "g05n2c08.png",
//				"g05n3p04.png",
				"g07n0g16.png","g07n2c08.png",
//				"g07n3p04.png",
				"g10n0g16.png","g10n2c08.png",
//				"g10n3p04.png",
				"g25n0g16.png","g25n2c08.png",
//				"g25n3p04.png",
				"oi1n0g16.png", "oi1n2c16.png", "oi2n0g16.png", "oi2n2c16.png", "oi4n0g16.png", "oi4n2c16.png",
				"oi9n0g16.png", "oi9n2c16.png",
				"pp0n2c16.png", "pp0n6a08.png", "ps1n0g08.png", "ps1n2c16.png", "ps2n0g08.png", "ps2n2c16.png",
//				"s01i3p01.png", "s01n3p01.png", "s02i3p01.png", "s02n3p01.png", "s03i3p01.png","s03n3p01.png",
//				"s04i3p01.png", "s04n3p01.png", "s05i3p02.png", "s05n3p02.png",
//				"s06i3p02.png", "s06n3p02.png",
//				"s07i3p02.png", "s07n3p02.png",
//				"s08i3p02.png", "s08n3p02.png",
//				"s09i3p02.png", "s09n3p02.png",
//				"s32i3p04.png", "s32n3p04.png", "s33i3p04.png", "s33n3p04.png", "s34i3p04.png", "s34n3p04.png",
//				"s35i3p04.png", "s35n3p04.png", "s36i3p04.png", "s36n3p04.png", "s37i3p04.png", "s37n3p04.png",
//				"s38i3p04.png", "s38n3p04.png", "s39i3p04.png", "s39n3p04.png",
//				"s40i3p04.png", "s40n3p04.png",
//				"tbbn1g04.png", "tbbn2c16.png", "tbbn3p08.png", "tbgn2c16.png", "tbgn3p08.png",
//				"tbrn2c08.png", "tbwn1g16.png", "tbwn3p08.png", "tbyn3p08.png",
				"tp0n1g08.png", "tp0n2c08.png", "tp0n3p08.png",
//				"tp1n3p08.png",
				"z03n2c08.png",
				"z06n2c08.png",
				"z09n2c08.png"
		);

		for (final String file : files) {
			final PngImage image = new PngImage("images/optimizer/pngsuite/" + file, "none");
			System.out.println(file);

			byte[] data = new PngReader().readRGBA8(image);
//			print(image, data);

//			System.out.println();

			byte[] data2 = Files.readAllBytes(new File("images/optimizer/pngsuite/" + file + ".rgba").toPath());
//			print(image, data2);

			assertTrue(equal(
					new BufferedInputStream(new ByteArrayInputStream(data)),
					new BufferedInputStream(new FileInputStream("images/optimizer/pngsuite/" + file + ".rgba"))
					)
			);
		}
	}

	private void print(PngImage image, byte[] data) {
		for (int i = 0; i < data.length; i += 4) {
			if (i % (image.getWidth() * 4) == 0) {
				System.out.println();
			}
			System.out.print(String.format("%2x", data[i]));
			System.out.print(String.format("%2x", data[i + 1]));
			System.out.print(String.format("%2x", data[i + 2]));
			System.out.print(String.format("%2x|", data[i + 3]));
		}
	}

	private boolean equal(final InputStream a, final InputStream b) throws IOException {
		int i = 0;
		try (InputStream in1 = a; InputStream in2 = b) {
			int value1, value2;
			do {
				value1 = in1.read();
				value2 = in2.read();
				if (value1 != value2) {
					System.out.println("Differs at " + i + ": value1=" + value1 + ", value2=" + value2);
					return false;
				}
				i++;
			} while (value1 >= 0);

			return in2.read() == -1;
		}
	}
}
