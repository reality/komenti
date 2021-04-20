package klib

import org.elasticsearch.client.*
import org.elasticsearch.action.*
import org.elasticsearch.action.index.*
import org.elasticsearch.action.search.*
import org.elasticsearch.search.builder.*
import org.elasticsearch.index.query.*
import org.elasticsearch.common.xcontent.XContentType 

import org.apache.http.*

class ElasticSearch {
  static def DEFAULT_ROOT = 'http://aber-owl.net/'
  static def DEFAULT_FIELD = 'text'

  static def searchDocuments(String fieldName, Vocabulary v) {
    def client = new RestHighLevelClient(RestClient.builder(new HttpHost("localhost", 9200, "http")))

    def query = new MatchQueryBuilder(fieldName, v.entities.collect { k, l -> l.label })
    def search = new SearchRequest()
      .source(new SearchSourceBuilder()
        .query(query))

    def response = client.search(search, RequestOptions.DEFAULT)

    println response
  }

  static def searchDocuments(Vocabulary v) {
    searchDocuments(DEFAULT_FIELD, v)
  }
}
