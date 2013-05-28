#!/usr/bin/python
import random
import nltk

f = open('output5.csv','r')
line = f.readline()
x = []
y = []
labels = []
limit = 100
while line != "":
	comps = line.strip().split()
	print comps
	l = comps[0][1:]
	if random.random() > 0.0 and len(l) > 6 and int(comps[4]) < limit and int(comps[4]) > 30:
		x.append(float(comps[2]))
        	labels.append(l)
		y.append(float(comps[3]))
	line = f.readline()

import matplotlib
import pylab
import matplotlib.pyplot as plt

plt.scatter(x,y)
for label, x1, y1 in zip(labels, x, y):
    plt.annotate(
        label, 
        xy = (x1, y1), xytext = (-1, -1),
        textcoords = 'offset points', ha = 'right', va = 'bottom')
plt.show()


