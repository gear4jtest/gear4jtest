//package io.github.gear4jtest.core.internal;
//
//import java.io.InputStream;
//import java.util.Map;
//
//import io.github.gear4jtest.core.model.ChainModel;
//
///**
// * Entry class for Gear4j chains execution.
// * 
// * @author quleb
// *
// */
//public class CompositeJsonChainExecutorFacade {
//	
//	private final ChainExecutorService chainExecutorFacade;
//	
//	public CompositeJsonChainExecutorFacade() {
//		this.chainExecutorFacade = new ChainExecutorService();
//	}
//	
//	public <BEGIN, IN> IN execute(InputStream is, BEGIN object, Map<String, Object> context) {
//		// Do whatever you want here
//		ChainModel<BEGIN, IN> chain = null; //buildChainFromInputStream();
//		return chainExecutorFacade.execute(chain, object, context);
//	}
//	
//}
