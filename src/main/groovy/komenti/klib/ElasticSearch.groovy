package klib

import groovyx.net.http.HTTPBuilder
import groovyx.net.http.HttpResponseException
import groovyx.gpars.GParsPool

class ElasticSearch {
  static def DEFAULT_ROOT = 'http://aber-owl.net/'
  static def DEFAULT_PORT = 9200
	static def DEFAULT_QUERY = [
		"query": [
			"terms": [
			]
		]
	]

  static def searchDocuments(String fieldName, Vocabulary v) {
		def query = new String(DEFAULT_QUERY)
    query["query"]["terms"][fieldName] = v.entities.collect { k, l -> l.label }

    // TODO: Do the query 

    return doccos 
  }
}
