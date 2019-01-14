package SocketBasedGame;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.Random;

/*This class extends Thread to allow it to run in its own thread. It handles all communication between the client and
 *the game.*/
public class GameService extends Thread {
    //This is the game that the current player will be playing.
    private Game game;
    //This is the player mark of the current player.
    private PlayerMark playerMark;
    //This is the socket used to communicate with the connected client.
    private Socket connection;
    //This is used to receive commands from the client.
    private BufferedReader input;
    //This is used to send responses to the client.
    private PrintWriter output;
    //Represents if a card is present. [DOUBLE, REPLACEMENT, FREEDOM].
    private boolean[] cards;
    //A boolean to flag if a player is blocked.
    private boolean isBlocked;

    //GameService constructor to initialise data members and get the streams from the socket.
    public GameService(Game game, Socket connection, PlayerMark playerMark) {
        this.game = game;
        this.playerMark = playerMark;
        this.connection = connection;
        try{
            input = new BufferedReader(new InputStreamReader(connection.getInputStream()));
            output = new PrintWriter(connection.getOutputStream(), true);
            //Welcomes the players and sends them their player mark for this game.
            output.println("MESSAGE Welcome. You have connected.");
            output.println("MARK " + playerMark.toString());
        } catch (IOException e) {
            e.printStackTrace();
        }
        cards = new boolean[]{true, true, true};
        isBlocked = false;
    }

    //Loop for receiving commands and sending them to be passed, until the END command is given.
    @Override
    public void run() {
        try{
            //Places the initial starting tile if possible
            placeInitialTile();
            //Loops while the game has not ended.
            while(true) {
                String[] command = input.readLine().trim().split(" ");
                System.out.println("Command was: " + command[0]);
                if(parseCommand(command).equals("END")){
                    break;
                }
            }
        } catch (IOException e) {
            game.endGame();
            this.interrupt();
        } finally {
            try{
                connection.close();
            } catch(IOException e){
                //Should have a problem with closing the socket.
            }
            this.interrupt();
        }
    }

    //Makes a move with the NONE influence card at a random point on the board.
    private void placeInitialTile(){
        Random r = new Random();

        boolean attemptPlace = true;
        //If the board is full then don't attempt to place a tile.
        if(game.boardFull()){
            game.setBlocked(playerMark.ordinal());
            attemptPlace = false;
        }

        //Otherwise, continue to place a tile on the board at a random place.
        while(attemptPlace) {
            //If a move is successfully made, then continue to update the games state.
            if(game.makeMove("NONE", r.nextInt(Game.ROWS), r.nextInt(Game.COLUMNS), playerMark.ordinal())){
                break;
            }
        }

        //Update all other player's boards.
        game.sendBoard();
        //Ensure the move didn't block any other players.
        game.checkBlocked();

        //Inform the client about who's turn it is.
        informClientOfTurn(game.getPlayerMarkTurn());

        //If that move ended then game, then don't proceed to the loop by calling endGame() and closing this thread.
        if(game.isGameOver()){
            //call game end game method
            game.endGame();
            this.interrupt();
        }
    }

    /*Parse the command sent by the client. If it is not the players turn, then they cannot issue any commands but the
     *end command*/
    private String parseCommand(String[] command){
        if(command[0].equals("END")){
            return "END";
        } else if(game.getPlayerMarkTurn() == playerMark) {
            if (command[0].equals("MOVE") && command.length == 4) {
                try{
                    String card = command[1];
                    int x = Integer.parseInt(command[2]);
                    int y = Integer.parseInt(command[3]);
                    if(game.makeMove(card, x, y, playerMark.ordinal())){
                        game.sendBoard();
                        game.checkBlocked();
                        if(InfluenceCard.valueOf(card) != InfluenceCard.DOUBLE) {
                            game.nextPlayer();
                        }
                        if(isBlocked) {
                            game.nextPlayer();
                        }
                        removeCard(card);
                        output.println("LEGAL_MOVE " + card);
                    }
                    else{
                        output.println("ILLEGAL_MOVE");
                    }
                } catch (NumberFormatException e){
                    output.println("INVALID_MOVE");
                } finally {
                    if(game.isGameOver()){
                        game.endGame();
                    }
                }
            } else {
                System.out.println(command[0]);
                output.println("MESSAGE Unknown Command");
            }
        } else {
            output.println("MESSAGE Not your turn.");
        }
        return "OK";
    }

    //Sets isBlocked to true.
    public void setBlocked(){
        isBlocked = true;
    }

    //Returns whether the player is blocked or not.
    public boolean isBlocked(){
        return isBlocked;
    }

    //Sets the players mark.
    public void setPlayerMark(PlayerMark mark){
        playerMark = mark;
    }

    //Returns the players mark.
    public PlayerMark getPlayerMark(){
        return playerMark;
    }

    //Returns the influence cards available to a player.
    public boolean[] availableCards() {
        return cards;
    }

    //Sends the board to the clients program.
    public void updateBoard(String command) {
        output.println("BOARD " + command);
    }

    //Informs the client that the game has ended and passes the final scores of the game.
    public void end(String scores){
        output.println("END " + scores);
    }

    //Removes one of the cards that was available to the player.
    private void removeCard(String card){
        if(!card.equals(InfluenceCard.NONE.toString())){
            cards[InfluenceCard.valueOf(card).ordinal()] = false;
        }
    }

    //Informs the client about who's turn it currently is by sending them the mark of that player.
    public void informClientOfTurn(PlayerMark mark){
        output.println("TURN " + mark);
    }
}