Milestones
==

We propose the following intermediate deliverables for Polaris.

The goal is to identify predictable points at which new features can be demonstrated.
The approach is a breadth-first march across all the functionality needed, after which
we can iterate on improving production-worthiness and performance until it is
ready for prime-time.
	
	v0: [ +1 week ] prototype
		- expose API (json over http)
		- ACL not required
		
	v1: [ +4 weeks ] functional minimum
		- postgresql integration
		- create, update, delete file content
		- ACL checks in place
		- 1 store per user
		
	v2: [ +4 weeks ] shared folders, migrate objects
		- create shared folders
		- sync shared folders
		- object migration between stores
		
	v3: [ +3? weeks ] convert the unwashed
		- tick conversion
	
	v4: [ +? weeks ] production integration
		- design for db/storage space integration
	
	v5..n: lather, rinse, repeat
		- performance test
		- improve & remeasure

The goal in this final phase: shake out any unplanned integration work, and 
get a measurement of velocity so we can predict a final release date.


Tasks, high-level
==

	External (HTTP) Interface
		No-op API Implementation
		Metrics integration
		-- v0 ready --

	Data Representation
		Basic schema implementation
		DAO layer (internal API)
		JDBI integration layer
		(metrics)


	Notification System
		Notification client integration


	ACL Library
		API Design
		Implementation - call through to canonical system
			Authentication mechanism for sparta calls?


	Business Logic
		Create objects
		Update/move objects
		List deleted objects in a parent
		-- v1 ready --
		Detect path conflicts
		Detect update conflicts
		Create shared folder
		Object migration
			Finalize requirements
			Design for scalable migration
			Implementation
			Tests for edge cases
				share a parent of a share
				move a shared folder inside a shared folder
				move a file out of a share into another share, make sure both users see consistent state
		-- v2 ready --
	
		Tick conversion
			Finalize design
			Implementation
			Integration testing
				version regression after conversion
				newer version than conversion (local conflict)
			
		-- v3 ready --


	Integration tasks
		Authentication
			Cert-based authentication mechanism for devices
				nginx frontend
				backend validation
			OAuth authentication for API requests
				integration with api server
			
		Configuration schema / reader
		Puppet scripting
		Local-production / appliance integration
		Health-check / metrics availability
		ACL library improvements
			Cache results locally
		-- v4 ready --

	
	Production-readiness
		Lather
		Rinse
		Repeat
