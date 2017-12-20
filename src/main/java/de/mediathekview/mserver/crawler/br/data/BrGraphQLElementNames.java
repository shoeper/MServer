/*
 * BrGraphQLElementNames.java
 * 
 * Projekt    : MServer
 * erstellt am: 14.12.2017
 * Autor      : Sascha
 * 
 */
package de.mediathekview.mserver.crawler.br.data;

public enum BrGraphQLElementNames {

  BOOLEAN_HAS_NEXT_PAGE("hasNextPage"),
  GRAPHQL_TYPE_ELEMENT("__typename"),
  ID_ELEMENT("id"),
  INT_COUNTER_ELEMENT("count"),
  STRING_CURSOR_ELEMENT("cursor")
  ;
  
  private String elementName;
  
  private BrGraphQLElementNames(String elementName) {
    this.elementName = elementName;
  }
  
  public String getName() {
    return this.elementName;
  }
  
}