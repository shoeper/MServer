/*
 * BrGraphQLNodeNames.java
 * 
 * Projekt    : MServer
 * erstellt am: 14.12.2017
 * Autor      : Sascha
 * 
 * (c) 2017 by Sascha Wiegandt
 */
package de.mediathekview.mserver.crawler.br.data;

public enum BrGraphQLNodeNames {

  RESULT_ROOT_NODE("data"),
  RESULT_ROOT_BR_NODE("viewer"),
  RESULT_CLIP_ID_ROOT("searchAllClips"),
  RESULT_PAGE_INFO("pageInfo"),
  RESULT_NODE_EDGES("edges"),
  RESULT_NODE("node")
  ;
  
  private String nodeName;
  
  private BrGraphQLNodeNames(String nodeName) {
    this.nodeName = nodeName;
  }
  
  public String getName() {
    return this.nodeName;
  }
  
}