package klib

import org.elasticsearch.client.*
import org.elasticsearch.action.*
import org.elasticsearch.action.index.*
import org.elasticsearch.action.search.*
import org.elasticsearch.search.builder.*
import org.elasticsearch.index.query.*
import org.elasticsearch.common.xcontent.XContentType 
import org.elasticsearch.common.unit.TimeValue

import org.apache.http.*

class ElasticSearch {
  static def DEFAULT_HOST = 'localhost'
  static def DEFAULT_PORT = 9200
  static def DEFAULT_GROUPBY = 'id'
  static def DEFAULT_FIELD = 'text'

  static def searchDocuments(o, Vocabulary v) {
    def host = o['host'] ?: DEFAULT_HOST
    def port = o['port'] ?: DEFAULT_PORT
    def field = o['field'] ?: DEFAULT_FIELD
    def groupBy = o['group-by'] ?: DEFAULT_GROUPBY

    def client = new RestHighLevelClient(RestClient.builder(
      new HttpHost(host, port, "http")))

    // Just building a boolean OR query for each of the labels in the vocab
    def query = new BoolQueryBuilder()
    v.entities.each { k, l -> 
      l.label.each {
        query.should(QueryBuilders.matchQuery(field, it))
      }
    }

    def search = new SearchRequest()
      .scroll(TimeValue.timeValueMinutes(1))
      .source(new SearchSourceBuilder()
        .size(10000)
        .query(query))

    def oBuilder = RequestOptions.DEFAULT.toBuilder()
    oBuilder.setHttpAsyncResponseConsumerFactory(
          new HttpAsyncResponseConsumerFactory
            .HeapBufferedResponseConsumerFactory(1024 * 1024 * 1024))
    def rOptions = oBuilder.build()


    def response = client.search(search, rOptions)
    def scrollId = response.getScrollId()
    def hitContainer = response.getHits()
    def hits = hitContainer.getHits()

    def total = [:]
    def totalHits = 0
    while(hits.length > 0) {
      totalHits += hits.length
      hits.each {
        def doc = it.getSourceAsMap()
        def g = doc[groupBy]
        if(!total.containsKey(g)) {
          total[g] = []
        }
        total[g] << doc[field]
        totalHits++
      }


      println hits.length
      println scrollId
      println ''

      // 'Scroll' to the next 'page' of 'results'
      def scrollRequest = new SearchScrollRequest(scrollId)
      scrollRequest.scroll(TimeValue.timeValueMinutes(1))
      response = client.scroll(scrollRequest, rOptions)
      scrollId = response.getScrollId() 
      hitContainer = response.getHits()
      hits = hitContainer.getHits()
    }

    println "${totalHits} total documents describing ${total.size()} entities grouped by ${groupBy}"

    client.close()
  }
}
