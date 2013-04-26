/**
 * The contents of this file are subject to the Mozilla Public License Version 1.1
 * (the "License"); you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.mozilla.org/MPL/
 * Software distributed under the License is distributed on an "AS IS" basis,
 * WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License for the
 * specific language governing rights and limitations under the License.
 *
 * The Original Code is "MinLLPWriterTest.java".  Description:
 * "Unit test class for ca.uhn.hl7v2.llp.MinLLPWriter"
 *
 * The Initial Developer of the Original Code is Leslie Mann. Copyright (C)
 * 2002.  All Rights Reserved.
 *
 * Contributor(s): ______________________________________.
 *
 * Alternatively, the contents of this file may be used under the terms of the
 * GNU General Public License (the  �GPL�), in which case the provisions of the GPL are
 * applicable instead of those above.  If you wish to allow use of your version of this
 * file only under the terms of the GPL and not to allow others to use your version
 * of this file under the MPL, indicate your decision by deleting  the provisions above
 * and replace  them with the notice and other provisions required by the GPL License.
 * If you do not delete the provisions above, a recipient may use your version of
 * this file under either the MPL or the GPL.
 *
 */
package ca.uhn.hl7v2.llp;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import ca.uhn.hl7v2.DefaultHapiContext;
import ca.uhn.hl7v2.HL7Exception;
import ca.uhn.hl7v2.app.Connection;
import ca.uhn.hl7v2.model.v25.message.ADT_A01;
import ca.uhn.hl7v2.util.LibraryEntry;
import ca.uhn.hl7v2.util.MessageLibrary;
import ca.uhn.hl7v2.util.RandomServerPortProvider;

/**
 * Unit test class for ca.uhn.hl7v2.llp.MinLLPWriter
 * 
 * @author Leslie Mann
 */
public class MinLLPWriterTest {
	// NB: Per the minimal lower layer protocol.
	// character indicating the termination of an HL7 message
	private static final char END_MESSAGE = '\u001c';
	// the final character of a message: a carriage return
	private static final char LAST_CHARACTER = 13;
	private static MessageLibrary messageLib;

	// character indicating the start of an HL7 message
	private static final char START_MESSAGE = '\u000b';
	private String message;
	private MinLLPWriter minLLPWriter;
	private Throwable myException;

	private int myPacketCount;

	private ByteArrayOutputStream outputStream;


	/*
	 * *********************************************************
	 * Test Cases*********************************************************
	 */

	@Before
	public void setUp() throws Exception {
		message = "This is a test HL7 message";
		minLLPWriter = new MinLLPWriter();
		outputStream = new ByteArrayOutputStream();
	}


	@After
	public void tearDown() throws Exception {
		message = null;
		minLLPWriter = null;
		outputStream = null;
	}


	/**
	 * Test closing writer with as if private access
	 */
	@Test
	public void testClosePrivateWriter() {
		try {
			minLLPWriter = new MinLLPWriter(outputStream);
			minLLPWriter.close();
		} catch (IOException ioe) {
			fail("Problem setting up test conditions");
		}
		try {
			minLLPWriter.writeMessage(message);
			fail("Output stream should be closed");
		} catch (IOException ioe) {
		} catch (LLPException llpe) {
		}
	}


	/**
	 * Test default constructor
	 */
	@Test
	public void testConstructor() {
		assertNotNull("Should have a valid MinLLPWriter object", minLLPWriter);
	}


	/**
	 * Ensure constructor validates inputs. Pass a null outputStream
	 */
	@Test
	public void testConstructorWithNullOutputStream() {
		ByteArrayOutputStream nullOutputStream = null;

		try {
			minLLPWriter = new MinLLPWriter(nullOutputStream);
			fail("Should not be able to create a MinLLPWriter with a null input stream");
		} catch (IOException ioe) {
		} catch (NullPointerException e) {
		}
	}


	/**
	 * Test constructor with output stream
	 */
	@Test
	public void testConstructorWithOutputStream() throws IOException {
		minLLPWriter = new MinLLPWriter(outputStream);
		assertNotNull("Should have a valid MinLLPWriter object", minLLPWriter);
	}


	/**
	 * Ensure setOutputStream validates inputs. Pass a null outputStream
	 */
	@Test
	public void testSetNullOutputStream() {
		ByteArrayOutputStream nullOutputStream = null;

		try {
			minLLPWriter.setOutputStream(nullOutputStream);
			fail("Should not be able to set a null output stream");
		} catch (IOException ioe) {
		} catch (NullPointerException e) {
		}
	}


	@Test
	public void testWithCharsetProperty() throws Exception {
		String test = "foo";
		String charset = "UTF-16"; // makes "foo" look like "???" with default
		                           // charset

		String before = System.getProperty(MinLLPWriter.CHARSET_KEY);
		System.setProperty(MinLLPWriter.CHARSET_KEY, charset);

		ByteArrayOutputStream out = new ByteArrayOutputStream();
		MinLLPWriter writer = new MinLLPWriter(out);
		writer.writeMessage(test);
		assertTrue(out.toString(charset).indexOf(test) >= 0);

		if (before != null) {
			System.setProperty(MinLLPWriter.CHARSET_KEY, before);
		} else {
			System.clearProperty(MinLLPWriter.CHARSET_KEY);
		}
	}


	@Test
	public void testWithSpecifiedCharset() throws Exception {
		String test = "foo";
		String charset = "UTF-16"; // makes "foo" look like "???" with default
		                           // charset
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		MinLLPWriter writer = new MinLLPWriter(out);
		writer.writeMessage(test, charset);
		assertTrue(out.toString(charset).indexOf(test) >= 0);
	}


	/**
	 * Test writeMessage with MessageLibrary contents
	 */
	@Test
	public void testWriteLibraryMessages() throws IOException, LLPException {
		minLLPWriter.setOutputStream(outputStream);
		int mismatch = 0;
		for (int i = 0; i < getMessageLib().size(); i++) {
			String msg = ((LibraryEntry) getMessageLib().get(i)).messageString();
			minLLPWriter.writeMessage(msg);
			String llpMessage = outputStream.toString();
			outputStream.reset();
			// minLLPWriter adds minimum lower layer protocol characters
			if (!(START_MESSAGE + msg + END_MESSAGE + LAST_CHARACTER).equals(llpMessage)) {
				mismatch++;
			}
		}

		assertEquals("HL7 message should equal oded message", 0, mismatch);
	}


	/**
	 * Testing writeMessage with various messages.
	 */
	@Test
	public void testWriteMessages() {
		class TestSpec {
			Object outcome;
			String writeMessage;


			TestSpec(String message, Object outcome) {
				if (message != null)
					this.writeMessage = message;
				else
					this.writeMessage = null;
				this.outcome = outcome;
			}


			public boolean executeTest() {
				try {
					ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
					MinLLPWriter minLLPWriter = new MinLLPWriter(outputStream);
					minLLPWriter.writeMessage(writeMessage);
					String string = outputStream.toString();
					boolean equals = string.equals(outcome);
					return equals;
				} catch (Exception e) {
					return (e.getClass().equals(outcome));
				}
			}


			public String toString() {
				return "[" + (writeMessage != null ? writeMessage.toString() : "null") + ":" + outcome + "]";
			}
		}// inner class

		TestSpec[] tests = { new TestSpec(null, NullPointerException.class), new TestSpec("", START_MESSAGE + "" + END_MESSAGE + LAST_CHARACTER), new TestSpec(message, START_MESSAGE + message + END_MESSAGE + LAST_CHARACTER), };

		List<TestSpec> failedTests = new ArrayList<TestSpec>();

		for (int i = 0; i < tests.length; i++) {
			if (!tests[i].executeTest())
				failedTests.add(tests[i]);
		}

		assertEquals("readMessages failures: " + failedTests, 0, failedTests.size());
	}


	/**
	 * Attempt to write a message without calling - <code>setOutputStream</code>
	 */
	@Test
	public void testWriteMessageWithoutOutputStream() throws LLPException {
		try {
			minLLPWriter.writeMessage(message);
			fail("Writer should be initialized before use");
		} catch (IOException ioe) {
		} catch (NullPointerException e) {
		}
	}


	/**
	 * This is a weird test- On old OS's we want to make sure that the entire MLLP block is
	 * sent in a single packet for a small message, because ancient versions of Websphere on AIX
	 * behave badly if we don't. Sigh! 
	 */
	@Test
	public void testWritesHappenInOnePacket() throws IOException, HL7Exception, LLPException {
		myException = null;
		myPacketCount = 0;
		int port = RandomServerPortProvider.findFreePort();
		final ServerSocket ss = new ServerSocket(port);
		ss.setSoTimeout(50);
		try {
			// start listening
			ss.accept();
		} catch (Exception e) {
			// ignore
		}

		Thread t = new Thread() {
			@Override
			public void run() {
				try {
					Socket s;
					do {
						try {
							s = ss.accept();
						} catch (Exception e) {
							s = null;
						}
					} while (s == null);
					InputStream is = s.getInputStream();
					while (true) {
						int count = is.available();
						if (count > 0) {
							myPacketCount++;
							System.out.println(count);
							byte[] array = new byte[count];
							is.read(array, 0, count);
							System.out.println(new String(array, "ISO-8859-1"));
						}
					}

				} catch (Throwable t) {
					t.printStackTrace();
					myException = t;
				}
			}
		};
		t.start();

		ADT_A01 msg = new ADT_A01();
		msg.initQuickstart("ADT", "A01", "T");

		Connection client = new DefaultHapiContext().newClient("localhost", port, false);
		client.getInitiator().setTimeoutMillis(100);
		try {
			client.getInitiator().sendAndReceive(msg);
		} catch (Exception e) {
			// expect a timeout
		}

		assertTrue(myException == null);
		assertEquals(1, myPacketCount);
	}


	public static MessageLibrary getMessageLib() {
		// only want to setup once
		if (messageLib == null) {
			String path = "ca/uhn/hl7v2/util/messages.txt";
			messageLib = new MessageLibrary(path, "VB");
		}

		return messageLib;
	}
}