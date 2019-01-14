package SocketBasedGame;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.net.*;

/**
 * The Protocol is as follows:
 * Server Sends: MESSAGE aMessage - Sends a message to the client.
 * Server Sends: MARK aPlayerMark - Sends a string which represents the player mark for that client.
 * Server Sends: BOARD M M M ... - Sends all the marks on the game board as a flattened array.
 * Server Sends: TURN aPlayerMark - Sends a string which represents the player mark of the current player for the turn.
 * Server Sends: LEGAL_MOVE influenceCard - Indicates the move was legal to the client and tells them the card used.
 * Server Sends: ILLEGAL_MOVE - Indicates the move was illegal to the client.
 * Server Sends: INVALID_MOVE - Indicates a MOVE command sent by the client was not formatted correctly.
 * Server Sends: END Winner Score Score ... - Indicates the winner to the client and the scores of all players.
 * Client Sends: MOVE influenceCard x y - Requests a tile to be placed at x y using the influenceCard.
 * Client Sends: END - Requests the server thread closes their connection and interrupts their own thread.
 */
public class GameServer {

    //The port that this server will be open on.
    public static final int PORT = 8080;

    //Main method entry point.
    public static void main(String[] args) throws IOException {
        //Construct a frame which has a default close operation to allow the host to see that it is running
        //and to be able to close the server by closing the window.
        JFrame frame = new JFrame();
        frame.setTitle("Server Window");
        frame.setSize(1000, 600);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setVisible(true);

        //Place a simple message in the middle of the window to indicate its purpose.
        JLabel serverText = new JLabel("This is the Server window. Close this window to end the server.");
        serverText.setHorizontalAlignment(SwingConstants.CENTER);
        frame.add(serverText, BorderLayout.CENTER);

        //Construct the initial Game object to be used by the server and passed to new GameService threads.
        Game game = new Game();

        //Setup a server socket with the port number, PORT.
        ServerSocket server = new ServerSocket(PORT);

        //Display informational messgages in the console.
        System.out.println("Started The Server On Port " + PORT);
        System.out.println("Waiting for clients to connect...");

        //Initialise the player count to 0.
        int playerCount = 0;

        //Continue to accept connections while the game is not finished.
        //This only exits if the server had 5 players and was just polling before the game finished.
        while (!game.isFinished()) {
            //Prevents more than 5 players connecting.
            //If there are 5 players, then this loop will wait 1 second before checking if the game is finished.
            if (playerCount < 5) {
                try {
                    //Accept a connection, create a GameService for the client and add the player to the game.
                    Socket connection = server.accept();
                    System.out.println("Client Connected");
                    playerCount += 1;
                    GameService gameService = new GameService(game, connection, PlayerMark.values()[playerCount]);
                    new Thread(gameService).start();
                    game.addPlayer(gameService);
                } catch (SocketException e) {
                    //This is only entered when a client leaves and closes a socket.
                    e.printStackTrace();
                }
            } else {
                //Makes the thread sleep for 1 second, effectively making it poll every second to see if the game ended.
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
    }
}