#!/bin/bash

# See: https://docs.github.com/en/authentication/keeping-your-account-and-data-secure/about-anonymized-urls#removing-an-image-from-camos-cache
# Badge host has 12 hour cache: https://github.com/softwaremill/maven-badges/issues/445
curl -X PURGE $NOVM_GH_BADGE_URL