package com.badar.muneer;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.GridLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.net.Socket;
import java.net.InetAddress;
import java.io.IOException;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import java.util.Formatter;
import java.util.Scanner;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;

public class TicTacToeClient extends JFrame implements Runnable
{
	private JTextField idField; // textfield to display player's mark.
	private JTextArea displayArea; // JTextArea to display output.
	private JPanel boardPanel; // panel for tic-tac-toe board.
	private JPanel panel2; // panel to hold board.
	private Square[][] board; //tic-tac-toe board.
	private Square currentSquare; // current square;
	private Socket connection; // connection to the server.
	private Scanner input; // input from server.
	private Formatter output; // output to server.
	private String ticTacToeHost; // host name for server.
	private String myMark; // this client's mark.
	private boolean myTurn; // determines which client's turn it is.
	private final String X_MARK = "X"; // mark for first client.
	private final String O_MARK = "O"; // mark for second client.


	// set up user-interface and board.
	public TicTacToeClient(String host)
	{
		ticTacToeHost = host;

		displayArea = new JTextArea(4, 30); // set up JTextArea.
		displayArea.setEditable(false);
		add(new JScrollPane(displayArea), BorderLayout.SOUTH);

		boardPanel = new JPanel(); // set up panel for squares in board.
		boardPanel.setLayout( new GridLayout( 3, 3, 0, 0 ) );

		board = new Square[3][3]; // create board.

		// loop over the rows in the board.
		for(int row = 0; row < board.length; row++)
		{
			// loop over the columns in the board.
			for(int column = 0; column < board[row].length; column++)
			{
				// create square
				board[row][column] = new Square(" ", row * 3 + column);
				boardPanel.add(board[row][column]); // add square.
			} // end inner loop.
		} // end outer loop.

		idField = new JTextField(); // set up textfield.
		idField.setEditable(false);
		add(idField, BorderLayout.NORTH);

		panel2 = new JPanel(); // set up panel to contain boardPanel.
		panel2.add(boardPanel, BorderLayout.CENTER); // add board panel.
		add(panel2, BorderLayout.CENTER); // add container panel.

		setSize(300, 225);
		setVisible(true);
		startClient();
	}

	public void startClient()
	{
		try
		{
			// make connection to server.
			connection = new Socket(InetAddress.getByName(ticTacToeHost), 12345);

			// get streams for input adn output
			input = new Scanner(connection.getInputStream());
			output = new Formatter(connection.getOutputStream());

		} // end try.
		catch(IOException e)
		{
			e.printStackTrace();
		}

		ExecutorService worker = Executors.newFixedThreadPool(1);
		worker.execute(this); // execute client
	}

	// control thread that allows continous update of displayArea.
	public void run()
	{
		myMark = input.nextLine(); // get player's mark(X or O)
		SwingUtilities.invokeLater(
			new Runnable()
			{
				public void run()
				{
					// display player's mark.
					idField.setText("You are player \"" + myMark + "\"");
				} // end method run
			} // end anonymous inner class.
			);

		myTurn = (myMark.equals(X_MARK)); // determine if clinet's turn.

		// recieve messages sent to client and output them.
		while(true)
		{
			if(input.hasNextLine())
				processMessage(input.nextLine());
		} // end while.
	} // end method run.


	private void processMessage(String message)
	{
		System.out.println("Message recieved: " + message);
		// valid move occurred.

		if(message.equals("Valid move."))
		{
			displayMessage("Valid move, please wait.\n");
			setMark(currentSquare, myMark); // set mark in square.
		} // end if

		else if(message.equals("Invalid move, try again"))
		{
			displayMessage(message + "\n"); // display invalid move.
			myTurn = true; // still this client's turn.
		} // end else if.

		else if(message.equals("Opponent moved"))
		{
			int location = input.nextInt(); // get move location.
			input.nextLine(); // skip newline after int location.
			int row = location / 3; // calucalate row.
			int column = location % 3; // calculate column.

			setMark(board[row][column], (myMark.equals(X_MARK) ? O_MARK : X_MARK)); // mark move.

			displayMessage("Opponent moved. Your turn.\n");
			myTurn = true; // now this client's turn.
		} // end else if.

		else 
			displayMessage(message + "\n"); // display the message
	} // end method processMessage.

	// manipulate displayArea in event-dispatch thread.
	private void displayMessage(final String messageToDisplay)
	{
		SwingUtilities.invokeLater(
			new Runnable()
			{
				public void run()
				{
					displayArea.append(messageToDisplay); // updates output.
				}
			}
		);
	}

	private void setMark(final Square squareToMark, final String mark)
	{
		SwingUtilities.invokeLater(

			new Runnable()
			{
				public void run()
				{
					squareToMark.setMark(mark); // set mark in square.
				}
			}

		);
	}

	public void sendClickedSquare(int location)
	{
		// if it is my turn.
		if(myTurn)
		{
			output.format("%d\n", location); // send location to server.
			output.flush();
			myTurn = false;

			System.out.println("Location send: " + location + "& myturn=" + myTurn);
		} // end if
	} // end method sendClickedSquare.

	// set current Sqaure.
	public void setCurrentSquare(Square square)
	{
		currentSquare = square; // set current square to argument.
	} // end method setCurrentSquare.

	private class Square extends JPanel
	{
		private String mark; // mark to be drawn in this square.
		private int location; // location of square.

		public Square(String squareMark, int squareLocation)
		{
			mark = squareMark;
			location = squareLocation;

			addMouseListener(
				new MouseAdapter()
				{
					public void mouseReleased(MouseEvent e)
					{
						setCurrentSquare(Square.this); // set current square.

						// send location fo this square.
						sendClickedSquare(getSquareLocation());
					}
				}

			);
		} // end constructor.

		public Dimension getPreferredSize()
		{
			return new Dimension(30, 30);
		}

		public Dimension getMinimumSize()
		{
			return getPreferredSize();
		}
		public void setMark(String newMark)
		{
			mark = newMark; // set mark of square.
			repaint();
		}

		public int getSquareLocation()
		{
			return location;
		}

		public void paintComponent(Graphics g)
		{
			super.paintComponent(g);
			g.drawRect(0, 0, 29, 29); // draw square.
			g.drawString(mark, 11, 20); // draw mark.
		}
	}
}