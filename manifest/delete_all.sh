kubectl delete -f  /root/livefeed/manifest/livefeed-1-pricing-audusd.yaml
kubectl delete -f  /root/livefeed/manifest/livefeed-1-pricing-nzdusd.yaml
kubectl delete -f  /root/livefeed/manifest/livefeed-1-pricing-eurjpy.yaml
kubectl delete -f  /root/livefeed/manifest/livefeed-1-pricing-eurusd.yaml
kubectl delete -f  /root/livefeed/manifest/livefeed-1-pricing-gbpchf.yaml
kubectl delete -f  /root/livefeed/manifest/livefeed-1-pricing-gbpusd.yaml
kubectl delete -f  /root/livefeed/manifest/livefeed-1-pricing-usdchf.yaml
kubectl delete -f  /root/livefeed/manifest/livefeed-1-pricing-usdcad.yaml
kubectl delete -f  /root/livefeed/manifest/livefeed-1-pricing-usdjpy.yaml
kubectl delete -f /root/livefeed/manifest/livefeed-1-signal-foresignal.yaml
kubectl delete -f /root/livefeed/manifest/livefeed-1-signal-oanda.yaml
date
echo "delete all pricing livefeeds done!"
