#!/bin/bash
for (( n=0; n<=40; n++ ))
do
python synthesize_gp.py > "logs/$n.out"
done
