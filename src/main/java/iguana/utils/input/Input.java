/*
 * Copyright (c) 2015, Ali Afroozeh and Anastasia Izmaylova, Centrum Wiskunde & Informatica (CWI)
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without 
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this 
 *    list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice, this 
 *    list of conditions and the following disclaimer in the documentation and/or 
 *    other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND 
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED 
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. 
 * IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, 
 * INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT 
 * NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, 
 * OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, 
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) 
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY 
 * OF SUCH DAMAGE.
 *
 */

package iguana.utils.input;

import java.io.*;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import iguana.utils.collections.hash.MurmurHash3;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.input.BOMInputStream;
import org.apache.commons.io.input.ReaderInputStream;


/**
 * 
 * Is backed by an integer array to support UTF-32. 
 * 
 * @author Ali Afroozeh
 *
 */
public class Input {

    public static final int DEFAULT_TAB_WIDTH = 8;

	private final int[] characters;
	
	/**
	 * This array keeps the line and column information associated with each input index.
	 */
	private final LineColumn[] lineColumns;
	
	/**
	 * Number of lines in the input.
	 */
	private int lineCount;
	
	private final IntArrayCharSequence charSequence;
	
	private final URI uri;
	
	private final int tabWidth;

    private final int hash;
	
	public static Input fromString(String s, URI uri) {
		try {
			return new Input(fromStream(IOUtils.toInputStream(s)), uri);
		} catch (IOException e) {
			assert false : "this should not happen from a string";
			e.printStackTrace();
		}
		throw new RuntimeException();
	}
	
	public static Input fromCharArray(char[] s, URI uri) {
		try {
			return new Input(fromStream(new ReaderInputStream(new CharArrayReader(s))), uri);
		} catch (IOException e) {
		    e.printStackTrace();
		}
		throw new RuntimeException("unexpected implementation exception");
		
	}
	
	public static Input empty() {
		return fromString("");
	}
	
	public static Input fromString(String s) {
		return fromString(s, URI.create("dummy:///"));
	}
		
	public static Input fromChar(char c) {
		int[] input = new int[1];
		input[0] = c;
		return new Input(input, URI.create("dummy:///"));
	}
	
	public static Input fromIntArray(int[] input) {
		return new Input(input, URI.create("dummy:///"));
	}

	private static int[] fromStream(InputStream in) throws IOException {
		BOMInputStream bomIn = new BOMInputStream(in, false);
		Reader reader = new BufferedReader(new InputStreamReader(bomIn));
		
		List<Integer> input = new ArrayList<>();

		int c = 0;
		while ((c = reader.read()) != -1) {
			if (!Character.isHighSurrogate((char) c)) {
				input.add(c);
			} else {
				int next = 0;
				if ((next = reader.read()) != -1) {
					input.add(Character.toCodePoint((char)c, (char)next));					
				}
			}
		}
		
		reader.close();
		
		int[] intInput = new int[input.size()];
		int i = 0;
		for (Integer v : input) {
			intInput[i++] = v;
		}

		return intInput;
	}
	
	public static Input fromIntArray(int[] input, URI uri) {
		return new Input(input, uri);
	}
	
	public static Input fromPath(String path) throws IOException {
		return fromFile(new File(path));
	}
	
	public static Input fromFile(File file) throws IOException {
		return new Input(fromStream(new FileInputStream(file)), file.toURI());
	}

    private Input(int[] input, URI uri) {
        this(input, uri, DEFAULT_TAB_WIDTH);
    }

	private Input(int[] input, URI uri, int tabWidth) {
		this.uri = uri;
		this.charSequence = new IntArrayCharSequence(input);
		this.tabWidth = tabWidth;

		// Add EOF (-1) to the end of characters array
		int length = input.length + 1;
		this.characters = new int[length];
		System.arraycopy(input, 0, characters, 0, length - 1);
		this.characters[input.length] = -1;
		
		lineColumns = new LineColumn[characters.length];
		calculateLineLengths();

        this.hash = MurmurHash3.fn().apply(characters);
	}
	
	public int charAt(int index) {
		return characters[index];
	}

	public int length() {
		return characters.length;
	}
	
	public boolean match(int start, int end, String target) {
		return match(start, end, toIntArray(target));
	}
	
	public boolean match(int start, int end, int[] target) {
		if(target.length != end - start) {
			return false;
		}
	 	
		int i = 0;
		while(i < target.length) {
			if(target[i] != characters[start + i]) {
				return false;
			}
			i++;
		}
		
		return true;
	}

	public boolean match(int from, String target) {
		return match(from, toIntArray(target));
	}
	
	public boolean matchBackward(int start, String target) {
		return matchBackward(start, toIntArray(target));
	}
	
	public boolean matchBackward(int start, int[] target) {
		if(start - target.length < 0) {
			return false;
		}
		
		int i = target.length - 1;
		int j = start - 1;
		while(i >= 0) {
			if(target[i] != characters[j]) {
				return false;
			}
			i--;
			j--;
		}
		
		return true;
	}
	
	public boolean match(int from, int[] target) {
		
		if(target.length > length() - from) {
			return false;
		}
		
		int i = 0;
		while(i < target.length) {
			if(target[i] != characters[from + i]) {
				return false;
			}
			i++;
		}
		
		return true;
	}
	
	public static int[] toIntArray(String s) {
		int[] array = new int[s.codePointCount(0, s.length())];
		for(int i = 0; i < array.length; i++) {
			array[i] = s.codePointAt(i);
		}
		return array;
	}
	
	public IntArrayCharSequence asCharSequence() {
		return charSequence;
	}
	
	public LineColumn getLineColumn(int index) {
		if(index < 0 || index >= lineColumns.length) {
			return new LineColumn(0, 0);
		}
		return lineColumns[index];
	}
 
	public int getLineNumber(int index) {
		if(index < 0 || index >= lineColumns.length) {
			return 0;
		}
		return lineColumns[index].getLineNumber();
	}
	
	public int getColumnNumber(int index) {
		if(index < 0 || index >= lineColumns.length) {
			return 0;
		}
		return lineColumns[index].getColumnNumber();
	}

    @Override
    public int hashCode() {
        return hash;
    }

    @Override
	public boolean equals(Object obj) {
		
		if(this == obj)
			return true;
		
		if(! (obj instanceof Input)) 
			return false;
		
		Input other = (Input) obj;
		
		return this.length() == other.length() &&
               Arrays.equals(characters, other.characters);
	}
	
	public PositionInfo getPositionInfo(int leftExtent, int rightExtent) {
		return new PositionInfo(leftExtent, 
								rightExtent - leftExtent, 
								getLineNumber(leftExtent), 
								getColumnNumber(leftExtent), 
								getLineNumber(rightExtent), 
								getColumnNumber(rightExtent));
	}
	

	private void calculateLineLengths() {
		int lineNumber = 1;
		int columnNumber = 1;

		// Empty input: only the end of line symbol
		if(characters.length == 1) {
			lineColumns[0] = new LineColumn(lineNumber, columnNumber);
			return;
		}
		
		for (int i = 0; i < characters.length; i++) {
			lineColumns[i] = new LineColumn(lineNumber, columnNumber);
			if (characters[i] == '\n') {
				lineCount++;
				lineNumber++;
				columnNumber = 1;
			} 
			else if (characters[i] == '\r') {
				columnNumber = 1;
			} 
			else if (characters[i] == '\t') { 
			  columnNumber += tabWidth;
			} 
			else {
				columnNumber++;
			}
		}
		
		// The end of the line char column as the last character
//		lineColumns[input.length - 1] = new LineColumn(lineColumns[input.length - 2]);
	}
		
	private static class LineColumn {

		private int lineNumber;
		private int columnNumber;
		
		public LineColumn(int lineNumber, int columnNumber) {
			this.lineNumber = lineNumber;
			this.columnNumber = columnNumber;
		}
		
		public int getLineNumber() {
			return lineNumber;
		}
		
		public int getColumnNumber() {
			return columnNumber;
		}
		
		@Override
		public String toString() {
			return "(" + lineNumber + ":" + columnNumber + ")";
		}
		
		@Override
		public boolean equals(Object obj) {
			if(this == obj)
				return true;
			
			if(!(obj instanceof LineColumn))
				return false;
			
			LineColumn other = (LineColumn) obj;
			return lineNumber == other.lineNumber && columnNumber == other.columnNumber;
		}
	}
	
	/**
	 * Returns a string representation of this input instance from the
	 * given start (including) and end (excluding) indices.
	 *  
	 */
	public String subString(int start, int end) {
		List<Character> charList = new ArrayList<>();
		
		for(int i = start; i < end; i++) {
			if (characters[i] == -1) continue;
			char[] chars = Character.toChars(characters[i]);
			for(char c : chars) {
				charList.add(c);
			}			
		}
		
		StringBuilder sb = new StringBuilder();
		for(char c : charList) {
			sb.append(c);
		}
		
		return sb.toString();
	}

    /**
     * Inserts the contents of the given input at the given input position
     */
    public Input insert(int i, Input input) {
        if (i < 0) throw new IllegalArgumentException("i cannot be negative.");
        if (i > length()) throw new IllegalArgumentException("i cannot be greater than " + length());

        int[] newChars = new int[length() + input.length() - 2];
        System.arraycopy(characters, 0, newChars, 0, i);
        System.arraycopy(input.characters, 0, newChars, i, input.length() - 1);
        System.arraycopy(characters, i, newChars, i + input.length() - 1, length() - i - 1);
        return new Input(newChars, uri);
    }
	
	@Override
	public String toString() {
		return subString(0, characters.length - 1);
	}
	
	public int getLineCount() {
		return lineCount;
	}
	
	public boolean isEmpty() {
		return length() == 1;
	}
	
	
	public URI getURI() {
		return uri;
	}
	
	public boolean isEndOfLine(int currentInputIndex) {
		return characters[currentInputIndex] == 0 || lineColumns[currentInputIndex + 1].columnNumber == 1;
	}
	
	public boolean isEndOfFile(int i) {
		return characters[i] == -1;
	}
	
	public boolean isStartOfLine(int i) {
		return i == 0 || lineColumns[i].columnNumber == 1;
	}
	
}
