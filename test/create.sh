#!/bin/sh
curl -XPOST -d '{"db": "kanche", "collections": ["vehicle"]}' -H "Content-Type: application/json" "http://127.0.0.1:5000/event_source"