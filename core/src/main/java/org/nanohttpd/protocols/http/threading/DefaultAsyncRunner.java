package org.nanohttpd.protocols.http.threading;

/*
 * #%L
 * NanoHttpd-Core
 * %%
 * Copyright (C) 2012 - 2016 nanohttpd
 * %%
 * Redistribution and use in source and binary forms, with or without modification,
 * are permitted provided that the following conditions are met:
 * 
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 * 
 * 3. Neither the name of the nanohttpd nor the names of its contributors
 *    may be used to endorse or promote products derived from this software without
 *    specific prior written permission.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED.
 * IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT,
 * INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
 * BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
 * OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 * #L%
 */

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.nanohttpd.protocols.http.ClientHandler;

/**
 * Default threading strategy for NanoHTTPD.
 * <p/>
 * <p>
 * By default, the server spawns a new Thread for every incoming request. These
 * are set to <i>daemon</i> status, and named according to the request number.
 * The name is useful when profiling the application.
 * </p>
 */
public class DefaultAsyncRunner implements IAsyncRunner {

	/**
	 * Patched: autumo-beetroot.
	 * Thread Pool.
	 */
	private ExecutorService executor = Executors.newCachedThreadPool();

    protected long requestCount;
    private final List<ClientHandler> running = Collections.synchronizedList(new ArrayList<ClientHandler>());

    /**
     * @return a list with currently running clients.
     */
    public List<ClientHandler> getRunning() {
        return running;
    }

    @Override
    public void closeAll() {
        // copy of the list for concurrency
        for (ClientHandler clientHandler : new ArrayList<ClientHandler>(this.running)) {
            clientHandler.close();
        }
        this.shutDownExecutorService();
    }

    @Override
    public void closed(ClientHandler clientHandler) {
        this.running.remove(clientHandler);
    }

    @Override
    public void exec(ClientHandler clientHandler) {
        ++this.requestCount;
        this.running.add(clientHandler);
        // Submit the clientHandler to the executor service
        executor.submit(() -> {
            try {
                // Set thread name for monitoring
                Thread.currentThread().setName("NanoHttpd Request Processor (#" + this.requestCount + ").");
                clientHandler.run(); // Assuming ClientHandler implements Runnable
            } catch (Exception e) {
                // Rethrow as a runtime exception
                throw new RuntimeException("Error processing client request", e);
            }
        });
    }

	/**
	 * Shutdown thread pool.
	 */
	private void shutDownExecutorService() {
		executor.shutdown();
	    try {
	        // Wait for tasks to complete for up to 60 seconds
	        if (!executor.awaitTermination(60, TimeUnit.SECONDS)) {
	        	executor.shutdownNow(); // Force shutdown if timeout occurs
	            if (!executor.awaitTermination(60, TimeUnit.SECONDS)) {
	                // Ignore.
	            }
	        }
	    } catch (InterruptedException ie) {
	        // If interrupted, force shutdown immediately
	    	executor.shutdownNow();
	        Thread.currentThread().interrupt(); // Restore interrupt status
	    }
	}
	
}
