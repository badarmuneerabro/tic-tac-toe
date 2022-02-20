// Server side of client/server Tic-Tac-Toe program.

package com.badar.muneer;

import java.awt.BorderLayout;
import java.net.ServerSocket;
import java.net.Socket;
import java.io.IOException;
import java.util.Formatter;
import java.util.Scanner;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.Condition;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.JFrame;

public class TicTacToeServer extends JFrame
{
	private String[] board = new String[9]; // tic-tac-toe board.
	private JTextArea outputArea; // for outputting moves.
	private Player[] players; // array of Players.
	private ServerSocket server;
	private int currentPlayer; // keeps track of player with current move.
	private final static int PLAYER_X = 0;
	private final static int PLAYER_O = 1;
	private final static String[] MARKS = {"X", "O"};
	private ExecutorService runGame;
	private Lock gameLock;
	private Condition otherPlayerConnected; // condition to wait for other player.
	private Condition otherPlayerTurn; // condition to wait for other player's turn.



	public TicTacToeServer()
	{
		super("Tic-Tac-Toe Server");

		// create ExecutorService with a thread for each player.

		runGame = Executors.newFixedThreadPool(2);

		gameLock = new ReentrantLock(); // create lock for the game;

		// condition variables.
		otherPlayerConnected = gameLock.newCondition();
		otherPlayerTurn = gameLock.newCondition();

		for(int i = 0; i < 9; i++)
			board[i] = new String(""); // create tic-tac-toe board.

		players = new Player[2];
		currentPlayer = PLAYER_X;

		try
		{
			server = new ServerSocket(12345, 2);
		}
		catch(IOException e)
		{
			e.printStackTrace();
			System.exit(1);
		} // end catch


		outputArea = new JTextArea();

		add(outputArea, BorderLayout.CENTER);
		outputArea.setText("Server awaiting connections\n");

		setSize(300, 300);
		setVisible(true);
	} // end constructor.


	public void execute()
	{
		for(int i = 0; i < players.length; i++)
		{
			try
			{
				players[i] = new Player(server.accept(), i);
				runGame.execute(players[i]); // execute player runnable.
			} // end try.
			catch(IOException e)
			{
				e.printStackTrace();
				System.exit(1); 
			}
		}

		gameLock.lock(); // lock game to signal player X's thread.

		try
		{
			players[PLAYER_X].setSuspended(false); // resume player X 
			otherPlayerConnected.signal(); // wake up player X's thread
		}
		finally
		{
			gameLock.unlock();
		}

	} // end method execute.

	private void displayMessage(final String messageToDisplay)
	{
		SwingUtilities.invokeLater(
			new Runnable()
			{
				@Override
				public void run()
				{
					outputArea.append(messageToDisplay);
				}
			} // end inner class.
		);
	}

	public boolean validateAndMove(int location, int player)
	{
		// while not current player, must wait for turn.
		while(player != currentPlayer)
		{
			gameLock.lock();

			try
			{
				otherPlayerTurn.await();
			}
			catch(InterruptedException e)
			{
				e.printStackTrace();
			}
			finally
			{
				gameLock.unlock();
			}
		}

		if(!isOccupied(location))
		{
			board[location] = MARKS[currentPlayer];
			currentPlayer = (currentPlayer + 1) %2; // change player.

			// let new current palyer know that move occured.

			players[currentPlayer].otherPlayerMoved(location);

			gameLock.lock(); // lock the game.

			try
			{
				otherPlayerTurn.signal(); // signal other palyer to continue.
			} // end try
			finally
			{
				gameLock.unlock();
			}

			return true; // notify player that move was valid.
		}

		else
			return false;
	} // end method validateAndMove.


	public boolean isOccupied(int location)
	{
		if(board[location].equals(MARKS[PLAYER_X]) || board[location].equals(MARKS[PLAYER_O]))
			return true;
		else
			return false;
	} // end method isOccupied.

	// place code in this method to determine whether game over.
	public boolean isGameOver()
	{
		if(checkHorizontally())
			return true;
		else if(checkVertically())
			return true;
		else if(checkDiagonally())
			return true;
		else
			return false;
	}

	private boolean checkHorizontally()
	{
		boolean isOver = true;
		for(int i = 0; i < 7; i += 3)
		{
			for(int j = i + 1; j < i + 3; j++) // j = i + 1: makes the j to be checked from next to i.
			{
				if(!board[i].equals(board[j]))
				{
					isOver = false;
					break;
				}
			}
		}

		return isOver;
	}

	private boolean checkVertically()
	{
		boolean isOver = true;

		for(int i = 0; i < 3; i++)
		{
			for(int j = i + 3; j < i + 7; j += 3 ) 
			{
				if(!board[i].equals(board[j]))
				{
					isOver = false;
					break;
				}
			}
		}

		return isOver;
	}

	private boolean checkDiagonally()
	{
		boolean isOver = false;

		if(board[0].equals(board[4]) && board[0].equals(board[8]))
			isOver = true;

		else if(board[2].equals(board[4]) && board[2].equals[board[6]])
			isOver = true;

		return isOver;
	}


	private class Player implements Runnable
	{
		private Socket connection;
		private Scanner input;
		private Formatter output;
		private int playerNumber;
		private String mark;
		private boolean suspended = true; // whether thread is suspeded.


		public Player(Socket socket, int number)
		{
			playerNumber = number; // store this player's number

			mark = MARKS[playerNumber];
			connection = socket; // store socket for the client.


			try
			{
				input = new Scanner(connection.getInputStream());
				output = new Formatter(connection.getOutputStream());
			} // end try.
			catch(IOException e)
			{
				e.printStackTrace();
				System.exit(1);
			}
		}


		public void otherPlayerMoved(int location)
		{
			output.format("Opponent moved\n");
			output.format("%d\n", location);
			output.flush();
		} // end method otherPlayerMoved.


		public void run()
		{
			try
			{
				displayMessage("Player " + mark + " connected\n");

				output.format("%s\n", mark); // send player's mark.
				output.flush();


				// if player X, wait for another player to arrive.

				if(playerNumber == PLAYER_X)
				{
					output.format("%s\n%s", "Player X connected", "Waiting for another player\n");

					output.flush();

					gameLock.lock(); // lock game to wait for second player.

					try
					{
						// here the thread is supended to make
						// player X wait for the other player to connect.
						while(suspended)
						{
							otherPlayerConnected.await(); // wait for player O.
						}
					}catch(InterruptedException e)
					{
						e.printStackTrace();
					}
					finally
					{
						gameLock.unlock(); // unlock game after second player.
					}


					// send message that other player connected.

					output.format("Other player connected. Your move.\n");
					output.flush();
				}

				else
				{
					output.format("Player O connected, please wait\n");
					output.flush();
				} // end else.

				while(!isGameOver())
				{
					int location = 0; //initialize move location.

					if(input.hasNext())
						location = input.nextInt(); // get move location.


					// check for valid move.
					if(validateAndMove(location, playerNumber))
					{
						displayMessage("\nlocation: " + location);
						output.format("Valid move.\n"); // notify client.
						output.flush();
					} // end if

					else
					{
						output.format("Invalid move, try again\n");
						output.flush();
					} // end else
				}// end while.

			}// end try
			finally
			{
				try
				{
					connection.close(); // close connection to client.
				}
				catch(IOException e)
				{
					e.printStackTrace();
					System.exit(1);
				}
			}
		} // end metho run


		public void setSuspended(boolean status)
		{
			suspended = status; // set value of suspended.
		} // end method setSuspended.
	} // end player class
} // end class TicTacToeServer.