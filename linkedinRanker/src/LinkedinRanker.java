import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Scanner;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.scribe.builder.ServiceBuilder;
import org.scribe.builder.api.LinkedInApi;
import org.scribe.model.OAuthRequest;
import org.scribe.model.Response;
import org.scribe.model.Token;
import org.scribe.model.Verb;
import org.scribe.model.Verifier;
import org.scribe.oauth.OAuthService;


public class LinkedinRanker {
	List<Connection> connections;
	
	private class Connection implements Comparable<Connection>{
		String name;
		String headline;
		String location;
		String industry;
		int rank;
		
		//{"headline":"Senior Software Engineer at IBM","lastName":"Fredricks","location":{"name":"San Francisco Bay Area","country":{"code":"us"}},"industry":"Computer Software","firstName":"John"}
		public Connection(JSONObject json) throws JSONException{
			name = json.getString("firstName")+" "+json.getString("lastName");
			headline = "";
			location = "";
			industry = "";
			if(json.has("headline")) headline = json.getString("headline").toLowerCase();
			if(json.has("location")) location = json.getJSONObject("location").getString("name").toLowerCase();
			if(json.has("industry")) industry = json.getString("industry").toLowerCase();
			rank = 0;
		}
		
		public void assignRank(String[] paramaters){
			for(int i = 0; i < paramaters.length; i++){
				if(headline.contains(paramaters[i]))
					rank++;
				if(location.contains(paramaters[i]))
					rank++;
				if(industry.contains(paramaters[i]))
					rank++;
			}
		}

		@Override
		public int compareTo(Connection o) {
			return ((Connection) o).rank - this.rank;
		}
		
		public String toString(){
			return "Rank: "+rank+". "+name;
		}
	}
	
	public LinkedinRanker(){
		connections = new LinkedList<Connection>();
	}
	
	public JSONArray getConnections(OAuthService service, Token accessToken) throws org.scribe.exceptions.OAuthException, JSONException{
		System.out.println("Getting your connections");
		String url = "https://api.linkedin.com/v1/people/~/connections?format=json";
		OAuthRequest request = new OAuthRequest(Verb.GET, url);
		service.signRequest(accessToken, request);
		Response response = request.send();
		JSONObject json = new JSONObject(response.getBody());
		JSONArray conns = json.getJSONArray("values");
		System.out.println("Wow you have "+conns.length()+" public connections! Way to network!");
		return conns;
	}
	
	public void parseConnections(JSONArray conns, String[] params) throws JSONException{
		for(int i = 0; i < conns.length(); i++){
			if(conns.getJSONObject(i).getString("id").equals("private")) continue;
			Connection conn = new Connection(conns.getJSONObject(i));
			conn.assignRank(params);
			connections.add(conn);
		}
	}
	
	public void rank() {
		Collections.sort(connections);
	}
	
	public boolean printList(int amount, boolean upper) {
		if(amount > connections.size()) amount = connections.size();
		int start = 0;
		if(!upper)
			start = connections.size()-amount;
		
		for(int i = 0; i < amount; i++){
			System.out.println(connections.get(start+i));
		}
		
		return true;
	}
	
	public static void main(String[] args) {
		//you may need to register an app with you linkedin account if these api_* don't work
		//heres the link to do so:
		//https://developer.linkedin.com/documents/authentication
		//and follow the first instruction to authenticate
		String api_key = "75dow5izcg7957";
		String api_secret = "ndKcdMbv5g15XZjF";
		String accessTokenSaveFile = "accessToken.txt";
		
		//first step, authorize this app to access your linkedin profile and info
		Scanner in = new Scanner(System.in);
		LinkedinRanker lr = new LinkedinRanker();
		
		//create service
		try {
			System.out.println("First, we need to connect to your Linkedin account");
			OAuthService service = new ServiceBuilder()
		    						    .provider(LinkedInApi.class)
		    						   	.apiKey(api_key)
		    						   	.apiSecret(api_secret)
		    						   	.build();
			
			
			//if we have a saved accessToken, use that
			File saveFile = new File(accessTokenSaveFile);
			Token accessToken = null;
			if(saveFile.exists()){
				System.out.println("Since you've already connected once, we'll use the save data to access it...");
				BufferedReader saveReader = new BufferedReader(new FileReader(saveFile));
				JSONObject json = new JSONObject(saveReader.readLine());
				saveReader.close();
				accessToken = new Token(json.getString("token"), json.getString("secret"));
				//accessToken = new Token("07a24256-8dc5-47bb-a0f3-4e6c6277ed51", "f9ef8f35-9933-4c08-9378-3d6e1867e269");
			} else {
				//get request token
				Token requestToken = service.getRequestToken();
				
				//have user sign in
				System.out.println("Since this is your first time using this application, we'll need to sign in...");
				System.out.println("Sign in at this website:");
			    System.out.println(service.getAuthorizationUrl(requestToken));
			    System.out.println("And paste the verification code here:");
			    System.out.println("->");
			    Verifier verifier = new Verifier(in.nextLine());
			    
			    //use this information to get the access token	    
		    	System.out.println("Requesting access to your account...");
		    	accessToken = service.getAccessToken(requestToken, verifier);
		    	System.out.println("Account is available!");
		    	
		    	//now save the token to be used if run again...
		    	System.out.println("Saving access token for future use...");
		    	JSONObject json = new JSONObject();
		    	json.put("token", accessToken.getToken());
		    	json.put("secret", accessToken.getSecret());
		    	
		    	BufferedWriter saveWriter = new BufferedWriter(new FileWriter(saveFile));
		    	saveWriter.write(json.toString());
		    	saveWriter.close();
			}
			
		    //now that we have the access token we can get the list of connections
			System.out.println("Next we get the list of your connections...");
		    JSONArray conns = lr.getConnections(service, accessToken);
			
		    //String[] params = {"los angeles", "san francisco", "banking", "finance"};
		    System.out.println("Now that we have your connections, we need to rank them...");
		    String[] params = {};
		    while(params.length == 0){
			    //next we need to ask the user for a comma separated list of words to search for
			    System.out.println("Input a comma separated list of words you wish to rank by:");
			    System.out.println("(e.g. computer, software, bay area, san francisco)");
			    System.out.println("->");
			    String par = in.nextLine();
			    params = par.toLowerCase().split(",\\s+");
		    }
		    
		    //now convert these to easier to handle and read objects
		    lr.parseConnections(conns, params);
		    
		    //now sort the list into a ranked list
		    System.out.println("Lastly we need to sort the list by their new ranks...");
		    lr.rank();
		    
		    //now print the ranked list
		    System.out.println("Now that we have our list, how many connections would you like to see?");
		    System.out.println("->");
		    int amount = in.nextInt();
		    in.nextLine();
		    System.out.println("And would you like to see the top or bottom "+amount+"?");
		    System.out.println("->");
		    String top = in.nextLine();
		    lr.printList(amount, top.equals("top"));
		    
		    //now we'll loop so they can look up more info if they want
		    boolean cont;
		    System.out.println("If you want to quit, enter q");
	    	System.out.println("->");
	    	cont = !in.nextLine().equals("q");
		    while(cont){
		    	System.out.println("How many connections would you like to see?");
			    System.out.println("->");
			    amount = in.nextInt();
			    in.nextLine();
			    System.out.println("And would you like to see the top or bottom "+amount+"?");
			    System.out.println("->");
			    top = in.nextLine();
			    lr.printList(amount, top.equals("top"));
			    
		    	System.out.println("If you want to quit, enter q");
		    	System.out.println("->");
		    	cont = !in.nextLine().equals("q");
		    }
		    
		    
		    
	    } catch(org.scribe.exceptions.OAuthException e) {
	    	System.out.println("Error: incorrect access token.");
	    	e.printStackTrace();
	    } catch (JSONException e) {
			System.out.println("Error: Failed accessing or creating a JSONObject or JSONArray.");
			e.printStackTrace();
		} catch (IOException e) {
			System.out.println("Error: Failed to access or close the save file.");
			e.printStackTrace();
		}
	}
}
