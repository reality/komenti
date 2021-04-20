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

    def query = new BoolQueryBuilder()
    v.entities.each { k, l -> 
      l.label.each {
        query.should(QueryBuilders.matchQuery(fieldName, it))
      }
    }

    def search = new SearchRequest()
      .source(new SearchSourceBuilder()
        .trackTotalHits(true)
        .query(query))

    def response = client.search(search, RequestOptions.DEFAULT)

    def hits = response.getHits()

    println "Total documents: ${hits.getTotalHits().value}"

    client.close()
  }

  static def searchDocuments(Vocabulary v) {
    searchDocuments(DEFAULT_FIELD, v)
  }
}
