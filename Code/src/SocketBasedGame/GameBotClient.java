package SocketBasedGame;

import javax.swing.*;
import java.awt.*;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.Random;

/*This class extends JFrame to allow it to be used to present a GUI to the client to play the game.
 *Also implements ActionListener to allow it to be used to handle the influence card selection via the radio buttons.
 *Takes user input, sends commands to the server and presents the games board on a GUI.*/
public class GameBotClient extends JFrame {

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
    private final JLabel none;
    private final JLabel dCard;
    private final JLabel rCard;
    private final JLabel fCard;
    private final JLabel lastCardUsed;
    //Label to indicate to the player whose turn it is.
    private final JLabel turnIndicator;

    //GameClient constructor to initialise data members and get the streams from the socket.
    public GameBotClient() {
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
        none = new JLabel("None");
        none.setHorizontalAlignment(SwingConstants.CENTER);

        dCard = new JLabel("Double-move");
        dCard.setHorizontalAlignment(SwingConstants.CENTER);

        rCard = new JLabel("Replacement");
        rCard.setHorizontalAlignment(SwingConstants.CENTER);

        fCard = new JLabel("Freedom");
        fCard.setHorizontalAlignment(SwingConstants.CENTER);

        lastCardUsed = new JLabel("Last Card: NONE");
        lastCardUsed.setHorizontalAlignment(SwingConstants.CENTER);

        //Instantiate the label for indicating the player whose turn it is.
        turnIndicator = new JLabel();
        turnIndicator.setHorizontalAlignment(SwingConstants.CENTER);

        //Add all radio buttons and the turn indicator label to a panel to add to the south of the frame.
        JPanel t1 = new JPanel();
        t1.setLayout(new GridLayout(1, 4, 2, 2));
        t1.add(none);
        t1.add(dCard);
        t1.add(rCard);
        t1.add(fCard);


        JPanel t2 = new JPanel();
        t2.setLayout(new GridLayout(1, 2, 2, 2));
        t2.add(lastCardUsed);
        t2.add(turnIndicator);

        JPanel toolbar = new JPanel();
        toolbar.setLayout(new GridLayout(2, 1));
        toolbar.add(t1);
        toolbar.add(t2);
        getContentPane().add(toolbar, BorderLayout.SOUTH);

        //Create a panel for the Tile objects to be placed in.
        JPanel boardPanel = new JPanel();
        boardPanel.setPreferredSize(new Dimension(500, 300));
        //Gives the appearance of black lines between the tiles.
        boardPanel.setBackground(Color.BLACK);
        boardPanel.setLayout(new GridLayout(Game.ROWS, Game.COLUMNS, 2, 2));

        /*For each point on the game board, create a Tile object for it and add it to the boardPanel.
         *In the bot implementation, the tiles do not need a mouse listener.*/
        for (int i = 0; i < Game.ROWS; i++) {
            for (int j = 0; j < Game.COLUMNS; j++) {
                board[i][j] = new Tile();
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
        String action;
        try {
            while (true) {
                response = input.readLine().trim().split(" ");
                System.out.println("Server Response: " + buildResponse(response));
                action = parseResponse(response);
                //If parseResponse returns PLAY, then it is the bots turn.
                if (action.equals("PLAY")) {
                    botPlay();
                } else if (action.equals("END")) {
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

    //This method coordinates the moves made by the bot when it is the bots turn.
    private void botPlay(){
        System.out.println("Entered botPlay");
        Random r = new Random();
        int x = r.nextInt(Game.ROWS);
        int y = r.nextInt(Game.COLUMNS);

        if(mustUseReplacement()){
            output.println("MOVE REPLACEMENT " + x + " " + y);
        } else if(mustUseFreedom() || countEmptyTiles() < ((Game.ROWS * Game.COLUMNS)/2) && cards[InfluenceCard.FREEDOM.ordinal()]) {
            output.println("MOVE FREEDOM " + x + " " + y);
        } else if(countEmptyTiles() > 2 && r.nextInt(20) < 5 && cards[InfluenceCard.DOUBLE.ordinal()]) {
            output.println("MOVE DOUBLE " + x + " " + y);
        } else{
            output.println("MOVE NONE " + x + " " + y);
        }
    }

    //Checks if the bot must use the replacement card. If the board is full or the don't have an adjacent tile.
    private boolean mustUseReplacement(){
        if((boardFull() || hasNoAdjacents()) && cards[InfluenceCard.REPLACEMENT.ordinal()]) {
            return true;
        } else {
            return false;
        }
    }

    //Checks if the bot must use the freedom card. If the board has empty tiles and they don't have an adjacent tile.
    private boolean mustUseFreedom() {
        if(!boardFull() && hasNoAdjacents() && cards[InfluenceCard.FREEDOM.ordinal()]){
            return true;
        } else {
            return false;
        }
    }

    //Checks the locally stored game board to see if its full.
    private boolean boardFull(){
        for(int[] row : gameBoard) {
            for(int column : row) {
                if(column == PlayerMark.NONE.ordinal()){
                    return false;
                }
            }
        }

        return true;
    }

    //Checks if a bot player has any free tiles adjacent to one of their own tiles.
    private boolean hasNoAdjacents(){
        for(int x = 0; x < Game.ROWS; x++) {
            for(int y = 0; y < Game.COLUMNS; y++) {
                if(gameBoard[x][y] == playerMark.ordinal()){
                    if(checkFreeAdjacent(x, y)){
                        return false;
                    }
                }
            }
        }

        //If not, return true.
        return true;
    }

    //Checks a tile for adjacent tiles with the same mark
    private boolean checkFreeAdjacent(int x, int y) {
        boolean valid = false;
        for (int i = -1; i <= 1; i++) {
            for (int j = -1; j <= 1; j++) {
                try {
                    //Attempts to check an adjacent tile.
                    valid = gameBoard[x + i][y + j] == PlayerMark.NONE.ordinal();
                    if (valid) {
                        return true;
                    }
                } catch (ArrayIndexOutOfBoundsException e) {
                    /*Catching an array index exception allows for the general method to be applied regardless of the
                     *tile position, otherwise, checks for corner and edge tiles would be needed.*/
                }
            }
        }
        return false;
    }

    //Count the number of empty tiles remaining on the board.
    private int countEmptyTiles(){
        int count = 0;
        for(int[] row : gameBoard) {
            for(int column : row) {
                if(column == PlayerMark.NONE.ordinal()){
                    count++;
                }
            }
        }

        return count;
    }

    //Takes and response and parses it to perform the appropriate actions.
    private String parseResponse(String[] response) {
        if (response[0].equals("LEGAL_MOVE")) {
            System.out.println("Client Output: legal move. Update influence cards.");
            InfluenceCard card = InfluenceCard.valueOf(response[1]);
            String ifDouble = "";
            //Remove the correct influence card, if any, and prevent selecting of that card.
            if (card == InfluenceCard.DOUBLE) {
                cards[card.ordinal()] = false;
                dCard.setText(dCard.getText() + " - USED");
                dCard.setEnabled(false);
                lastCardUsed.setText("Last Card: " + card.toString());
                try{
                    Thread.sleep(1500);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                ifDouble = "PLAY";
            } else if (card == InfluenceCard.REPLACEMENT) {
                cards[card.ordinal()] = false;
                rCard.setText(rCard.getText() + " - USED");
                rCard.setEnabled(false);
            } else if (card == InfluenceCard.FREEDOM) {
                cards[card.ordinal()] = false;
                fCard.setText(fCard.getText() + " - USED");
                fCard.setEnabled(false);
            }
            lastCardUsed.setText("Last Card: " + card.toString());
            return ifDouble;
        } else if (response[0].equals("ILLEGAL_MOVE")) {
            //Tell the client the move was illegal.
            System.out.println("Client Output: Illegal Move.");
            //If this response was sent, then the bot made an illegal move so should try again.
            return "PLAY";
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
            //If the marks are the same, then its the bots turn. So return PLAY.
            if(playerMark.ordinal() == playerTurn.ordinal()){
                try{
                    Thread.sleep(1500);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                System.out.println("Bot should play");
                return "PLAY";
            } else {
                return "NOPLAY";
            }
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

//    //Allows this class to be used to handle the selection of the influence cards via the radio buttons.
//    @Override
//    public void actionPerformed(ActionEvent e) {
//        switch (e.getActionCommand()) {
//            case "None":
//                selectedCard = InfluenceCard.NONE;
//                break;
//            case "Double":
//                selectedCard = InfluenceCard.DOUBLE;
//                break;
//            case "Replacement":
//                selectedCard = InfluenceCard.REPLACEMENT;
//                break;
//            case "Freedom":
//                selectedCard = InfluenceCard.FREEDOM;
//            default:
//                break;
//        }
//    }

    //Main entry point for the client program.
    public static void main(String[] args) {
        GameBotClient gameBotClient = new GameBotClient();
        gameBotClient.play();
    }
}

//TODO: REMOVE THIS
//else if (response[0].equals("UPDATE")) {
//        try{
//        int x = Integer.parseInt(response[1]);
//        int y = Integer.parseInt(response[2]);
//        int mark = Integer.parseInt(response[3]);
//        board[x][y].setColor(colours[mark]);
//        board[x][y].repaint();
//        System.out.println("Client Output: Updated square (" + y + ", " + x + ")");
//        } catch(NumberFormatException e) {
//        e.printStackTrace();
//        }
//        this.repaint();
//}