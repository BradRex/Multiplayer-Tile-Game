package SocketBasedGame;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

/*This class extends JFrame to allow it to be used to present a GUI to the client to play the game.
 *Also implements ActionListener to allow it to be used to handle the influence card selection via the radio buttons.
 *Takes user input, sends commands to the server and presents the games board on a GUI.*/
public class GameClient extends JFrame implements ActionListener {

    //The IP that the client will use to connect to the server.
    private static final String SERVER = "localhost";
    //The port the client will connect to the server through.
    private static final int PORT = 8080;
    //Color array to colour the GUI in the colours corresponding to the player marks.
    private static Color[] colours = new Color[]{Color.WHITE, Color.RED, Color.GREEN, Color.BLUE, Color.BLACK, Color.PINK};
    //This is the socket used to communicate with the connected server.
    private Socket connection;
    //This is used to receive responses from the server.
    private BufferedReader input;
    //This is used to send commands to the server.
    private PrintWriter output;
    //Represents if a card is present. [DOUBLE, REPLACEMENT, FREEDOM].
    private boolean[] cards;
    //Holds the currently selected influence card.
    private InfluenceCard selectedCard;
    //This is the player mark of the current player.
    private PlayerMark playerMark;
    //This is the player mark of the player whose turn it currently is.
    private PlayerMark playerTurn;

    //Java GUI related data members:
    //Represents the board as Tile objects so they can be made clickable and be repainted.
    private Tile[][] board = new Tile[Game.ROWS][Game.COLUMNS];
    //This is the board represented as ints which indicate the mark at that position. Used by the bot.
    private int[][] gameBoard = new int[Game.ROWS][Game.COLUMNS];
    //Radio buttons for selecting the influence cards.
    private final JRadioButton none;
    private final JRadioButton dCard;
    private final JRadioButton rCard;
    private final JRadioButton fCard;
    //Label to indicate to the player whose turn it is.
    private final JLabel turnIndicator;

    //GameClient constructor to initialise data members and get the streams from the socket.
    public GameClient() {
        try {
            System.out.println("Getting connection");
            connection = new Socket(SERVER, PORT);
            input = new BufferedReader(new InputStreamReader(connection.getInputStream()));
            output = new PrintWriter(connection.getOutputStream(), true);
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, "Connection Failed. You Are Not Connected.", "Connection Failure", JOptionPane.ERROR_MESSAGE);
            e.printStackTrace();
            System.exit(1);
        }

        cards = new boolean[]{true, true, true};
        selectedCard = InfluenceCard.NONE;

        //Setup the basic GUI frame.
        setTitle("Client GameService Window");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setVisible(true);
        setResizable(false);

        //Configure the Radio buttons for the influence card selection.
        none = new JRadioButton("None");
        none.setActionCommand("None");
        none.setSelected(true);
        none.addActionListener(this);

        dCard = new JRadioButton("Double-move");
        dCard.setActionCommand("Double");
        dCard.addActionListener(this);

        rCard = new JRadioButton("Replacement");
        rCard.setActionCommand("Replacement");
        rCard.addActionListener(this);

        fCard = new JRadioButton("Freedom");
        fCard.setActionCommand("Freedom");
        fCard.addActionListener(this);

        //Add the radio buttons to one single group.
        ButtonGroup cardGroup = new ButtonGroup();
        cardGroup.add(none);
        cardGroup.add(dCard);
        cardGroup.add(rCard);
        cardGroup.add(fCard);

        //Instantiate the label for indicating the player whose turn it is.
        turnIndicator = new JLabel();

        //Add all radio buttons and the turn indicator label to a panel to add to the south of the frame.
        JPanel toolbar = new JPanel();
        toolbar.setLayout(new GridLayout(1, 5, 2, 2));
        toolbar.add(none);
        toolbar.add(dCard);
        toolbar.add(rCard);
        toolbar.add(fCard);
        toolbar.add(turnIndicator);
        getContentPane().add(toolbar, BorderLayout.SOUTH);

        //Create a panel for the Tile objects to be placed in.
        JPanel boardPanel = new JPanel();
        boardPanel.setPreferredSize(new Dimension(500, 300));
        //Gives the appearance of black lines between the tiles.
        boardPanel.setBackground(Color.BLACK);
        boardPanel.setLayout(new GridLayout(Game.ROWS, Game.COLUMNS, 2, 2));

        /*For each point on the game board, create a Tile object for it and add it to the boardPanel.
         *Before each tile is selected add a MouseListener to allow it to be clicked.*/
        for (int i = 0; i < Game.ROWS; i++) {
            for (int j = 0; j < Game.COLUMNS; j++) {
                final int fi = i, fj = j;
                board[i][j] = new Tile();
                board[i][j].addMouseListener(new MouseAdapter() {
                    @Override
                    public void mousePressed(MouseEvent e) {
                        //Depending on the card being used, send the appropriate command with the coords of this tile.
                        if (selectedCard == InfluenceCard.NONE) {
                            output.println("MOVE NONE " + fi + " " + fj);
                        } else if (selectedCard == InfluenceCard.DOUBLE) {
                            if (cards[InfluenceCard.DOUBLE.ordinal()]) {
                                output.println("MOVE DOUBLE " + fi + " " + fj);
                            } else {
                                System.out.println("Card Not Available");
                            }
                        } else if (selectedCard == InfluenceCard.REPLACEMENT) {
                            if (cards[InfluenceCard.REPLACEMENT.ordinal()]) {
                                output.println("MOVE REPLACEMENT " + fi + " " + fj);
                            } else {
                                System.out.println("Card Not Available");
                            }
                        } else if (selectedCard == InfluenceCard.FREEDOM) {
                            if (cards[InfluenceCard.FREEDOM.ordinal()]) {
                                output.println("MOVE FREEDOM " + fi + " " + fj);
                            } else {
                                System.out.println("Card Not Available");
                            }
                        }
                    }
                });
                boardPanel.add(board[i][j]);
            }
        }

        //Add the board panel to the centre of the frame.
        getContentPane().add(boardPanel, BorderLayout.CENTER);
        //Set the width and height to fit the contents of the frame.
        pack();
    }

    //Initialises the client object to start taking commands and allow the player to player the game from the GUI.
    private void play() {
        String[] response;
        try {
            while (true) {
                response = input.readLine().trim().split(" ");
                System.out.println("Server Response: " + buildResponse(response));
                if (parseResponse(response).equals("END")) {
                    break;
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                connection.close();
            } catch (IOException e) {
                //Shouldn't be reached.
                e.printStackTrace();
            }
        }
    }

    //Takes and response and parses it to perform the appropriate actions.
    private String parseResponse(String[] response) {
        if (response[0].equals("LEGAL_MOVE")) {
            System.out.println("Client Output: legal move. Update influence cards.");
            InfluenceCard card = InfluenceCard.valueOf(response[1]);
            //Remove the correct influence card, if any, and prevent selecting of that card.
            if (card == InfluenceCard.DOUBLE) {
                cards[card.ordinal()] = false;
                dCard.setEnabled(false);
            } else if (card == InfluenceCard.REPLACEMENT) {
                cards[card.ordinal()] = false;
                rCard.setEnabled(false);
            } else if (card == InfluenceCard.FREEDOM) {
                cards[card.ordinal()] = false;
                fCard.setEnabled(false);
            }
            selectedCard = InfluenceCard.NONE;
            none.setSelected(true);
        } else if (response[0].equals("ILLEGAL_MOVE")) {
            //Tell the client the move was illegal.
            System.out.println("Client Output: Illegal Move.");
        } else if (response[0].equals("INVALID_MOVE")) {
            //Tell the client the move was invalid
            System.out.println("Client Output: Invalid Move.");
        } else if (response[0].equals("BOARD")) {
            //Update the clients board and repaint the GUI tiles the corresponding colours.
            int index;
            for (int i = 0; i < Game.ROWS; i++) {
                for (int j = 0; j < Game.COLUMNS; j++) {
                    index = Integer.parseInt(response[1 + ((i * Game.COLUMNS) + j)]);
                    gameBoard[i][j] = index;
                    board[i][j].setColor(colours[index]);
                    board[i][j].repaint();
                }
            }
        } else if (response[0].equals("MARK")) {
            //Adds the players mark.
            System.out.println("Client Output: Adding player playerMark");
            playerMark = PlayerMark.valueOf(response[1]);
        } else if (response[0].equals("TURN")) {
            //Updates the mark indicating the player whose turn it currently is.
            playerTurn = PlayerMark.valueOf(response[1]);
            updateTurnIndicator();
        } else if (response[0].equals("MESSAGE")) {
            //Output the message sent from the server.
            System.out.println("Client Output: " + buildResponse(response));
        } else if (response[0].equals("END")) {
            //Decide if the player is a winner or a loser and display an appropriate message in a pop-up box.
            String scores = "";
            for (int i = 2; i < response.length; i++) {
                scores += PlayerMark.values()[i - 1].toString() + ":" + response[i] + " | ";
            }
            if (response[1].equals(playerMark.toString())) {
                JOptionPane.showMessageDialog(this, "WINNER! " + scores, "Game Finished. Final Scores", JOptionPane.INFORMATION_MESSAGE);
            } else {
                JOptionPane.showMessageDialog(this, "LOSER! " + scores, "Game Finished. Final Scores", JOptionPane.INFORMATION_MESSAGE);

            }
            System.out.println("Client Output: Ending session.");
            //Tell the server to end their connection too.
            output.println("END");
            //Return end to exit the loop in the play() method.
            return "END";
        }
        //Return OK to continue the loop in the play() method.
        return "OK";
    }

    //Construct a single string from all the command elements.
    private String buildResponse(String[] response) {
        String rspString = "";
        for (String word : response) {
            rspString += word + " ";
        }
        return rspString;
    }

    //Update the turn indicator to display the correct players turn.
    private void updateTurnIndicator() {
        if (playerTurn.toString().equals(PlayerMark.NONE.toString())) {
            turnIndicator.setText("STARTING...");
        } else if (playerTurn.toString().equals(playerMark.toString())) {
            turnIndicator.setText("YOUR TURN");
        } else {
            turnIndicator.setText(playerTurn.toString() + "'s TURN");
        }
    }

    //A simple inner class that extends JPanel to be drawn on the GUI. Can have its colour changed.
    class Tile extends JPanel {

        public Tile() {
            setBackground(Color.WHITE);
        }

        public void setColor(Color color) {
            setBackground(color);
        }
    }

    //Allows this class to be used to handle the selection of the influence cards via the radio buttons.
    @Override
    public void actionPerformed(ActionEvent e) {
        switch (e.getActionCommand()) {
            case "None":
                selectedCard = InfluenceCard.NONE;
                break;
            case "Double":
                selectedCard = InfluenceCard.DOUBLE;
                break;
            case "Replacement":
                selectedCard = InfluenceCard.REPLACEMENT;
                break;
            case "Freedom":
                selectedCard = InfluenceCard.FREEDOM;
            default:
                break;
        }
    }

    //Main entry point for the client program.
    public static void main(String[] args) {
        GameClient gameClient = new GameClient();
        gameClient.play();
    }
}