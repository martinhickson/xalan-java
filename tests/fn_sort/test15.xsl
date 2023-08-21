<?xml version="1.0"?>
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
                version="3.0">             

   <!-- Author: mukulg@apache.org -->
   
   <!-- An XSLT stylesheet to test, XPath 3.1 fn:sort function to sort sequence
        of text strings, both in ascending and descending orders.
        
        For this stylesheet example, the text data to be sorted, is from an 
        ASCII English alphabet (with characters codepoint values in range 
        U+003A to U+007A).
        
        Ref : https://en.wikipedia.org/wiki/Basic_Latin_(Unicode_block).        
   -->
   
   <xsl:output method="xml" indent="yes"/>
   
   <!-- a variable, having a sequence of text strings, that we wish to sort -->
   <xsl:variable name="seq1" select="('Noah','Michael','Gary','Mukul','Dave')"/>
   
   <!-- We use this variable declaration, to help create a sort key for function fn:sort,
        to sort the data in descending order. This is an unicode codepoint integer
        value, of the last character of the language alphabet we've chosen (the
        codepoint hex value of this character is U+007A). -->
   <xsl:variable name="alphabetLastCodepointValue" select="122"/>
   
   <xsl:variable name="fnGetcodepointsForReverseSortKey" select="function($str) { for $idx in (1 to string-length($str)) 
                                                                                        return ($alphabetLastCodepointValue - string-to-codepoints(substring($str, $idx, 1))) }"/>
   
   <xsl:template match="/">
      <result>
         <sorted1 sortOrder="ascending"><xsl:copy-of select="sort($seq1, (), function($str) { string($str) })"/></sorted1>
         
         <sorted2 sortOrder="descending"><xsl:copy-of select="sort($seq1, (), function($str) { 
                                                                                 codepoints-to-string($fnGetcodepointsForReverseSortKey(string($str))) 
                                                                              })"/></sorted2>
      </result>
   </xsl:template>
   
   <!--
      * Licensed to the Apache Software Foundation (ASF) under one
      * or more contributor license agreements. See the NOTICE file
      * distributed with this work for additional information
      * regarding copyright ownership. The ASF licenses this file
      * to you under the Apache License, Version 2.0 (the  "License");
      * you may not use this file except in compliance with the License.
      * You may obtain a copy of the License at
      *
      *     http://www.apache.org/licenses/LICENSE-2.0
      *
      * Unless required by applicable law or agreed to in writing, software
      * distributed under the License is distributed on an "AS IS" BASIS,
      * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
      * See the License for the specific language governing permissions and
      * limitations under the License.
   -->

</xsl:stylesheet>