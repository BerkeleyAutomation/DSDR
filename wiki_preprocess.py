#!/usr/bin/python
import re
import string
import HTMLParser  
import nltk

from nltk.corpus import brown
brown_news_tagged = brown.tagged_sents(categories='news')
tagger = nltk.UnigramTagger(brown_news_tagged[:500])

file = open('datasets/enwiki-latest-pages-articles-1pct.xml','r')
docword = open('docword.txt','w')
vocab = open('vocab.txt','w')

line = file.readline()

dictionary = {}
in_doc = False
brackets = re.compile(r'\[[^\]]*\]')
braces = re.compile(r'{[^}]*}')
special = HTMLParser.HTMLParser()
punctuation_strip = string.maketrans("","")
doc_id = 0
uw = 1
while line != "":
	if "<text" in line:
		in_doc = True
		doc_id = doc_id+1
        if "</text" in line:
		in_doc = False	

	if in_doc:
		line = line.replace("ref"," ")
		line = re.sub(brackets, '', line)
		line = re.sub(braces, '', line)
		if line[0].isalpha():
			line = line.lower().translate(punctuation_strip, string.punctuation)
			tokens = set(line.split())
			for t in tokens:
				if tagger.tag([t])[0][1] != 'NN':
					continue

				if dictionary.has_key(t):
					docword.write(str(doc_id) + " " + str(dictionary[t])+" "+str(1)+ "\n")
				else:
					dictionary[t] = uw
					vocab.write(t+"\n")
					uw = uw + 1
					docword.write(str(doc_id) + " " + str(dictionary[t])+" "+str(1)+ "\n")
					

        line = file.readline()	
