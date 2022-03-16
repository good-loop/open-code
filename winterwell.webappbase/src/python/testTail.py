from multiprocessing.connection import wait
import os
import time

for i in range(0,100):
	fh = open('/home/wing/Desktop/read.txt', 'w')
	fh.writelines('log something '+ str(i) +'\n')
	print(str(i))
	fh.close
	time.sleep(5)