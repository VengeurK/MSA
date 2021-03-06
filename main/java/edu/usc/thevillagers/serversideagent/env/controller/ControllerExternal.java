package edu.usc.thevillagers.serversideagent.env.controller;

import edu.usc.thevillagers.serversideagent.env.Environment;
import edu.usc.thevillagers.serversideagent.request.DataSocket;

/**
 * An external controller that behaves according to a TCP connection.
 */
public class ControllerExternal extends Controller {

	private final DataSocket sok;
	
	public ControllerExternal(Environment env, DataSocket sok) {
		super(env);
		this.sok = sok;
		try {
			if(sok.in.readBoolean()) setRecord(sok.in.readUTF());
		} catch (Exception e) {
			System.err.println("Cannot set record "+e);
		}
	}

	@Override
	public void step(boolean done) {
		try {
			sok.out.writeBoolean(done);
			sok.out.flush();
			state = ControllerState.values()[sok.in.read()];
			if(state == ControllerState.LOAD) stateParam = sok.in.readInt();
		} catch (Exception e) {
			state = ControllerState.TERMINATE;
		}
	}
}
