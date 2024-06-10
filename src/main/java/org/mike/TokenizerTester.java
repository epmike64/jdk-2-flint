package org.mike;


import com.flint.tools.flintc.parser.Lexer;
import com.flint.tools.flintc.parser.Token;
import com.flint.tools.flintc.util.Context;
import com.flint.tools.flintc.parser.ScannerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

public class TokenizerTester {

	Lexer S;
	Token token;

	TokenizerTester(Lexer S){
		this.S = S;
	}
	public void nextToken() {
		S.nextToken();
		token = S.token();
	}

	static String readFile(String path)  {
		try {
			return new String(Files.readAllBytes(Paths.get(path)));
		} catch (IOException e) {
			e.printStackTrace();
		}
		return null;
	}

	public static void main(String[] args) {
		if(args.length == 0) {
			System.out.println("Please provide the java file path as an argument");
			return;
		}
		String javaFileContent = readFile(args[0]);
		if(javaFileContent == null) {
			System.out.println("Error reading file");
			System.exit(1);
		}
		Context context = new Context();
		ScannerFactory scannerFactory = ScannerFactory.instance(context);
		Lexer lexer = scannerFactory.newScanner(javaFileContent);
		TokenizerTester tokenizerTester = new TokenizerTester(lexer);
		tokenizerTester.nextToken();
		while(tokenizerTester.token != null) {
			System.out.println("TokenKind: " + tokenizerTester.token.kind + " " + tokenizerTester.token.kind.name());
			tokenizerTester.nextToken();
		}
	}
}
